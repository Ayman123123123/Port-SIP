package com.chatapp.modern.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chatapp.modern.engine.Call
import com.chatapp.modern.engine.CallEngine
import com.chatapp.modern.engine.CallState
import com.chatapp.modern.engine.CallStateObserver
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real call engine backed by Google's WebRTC (BSD-3-Clause).
 *
 * The engine owns a [PeerConnectionFactory] and a [PeerConnection], registers the
 * local user with the signaling server so it can receive calls, and drives the
 * SDP offer/answer + ICE candidate exchange. It exposes live video through
 * [VideoProvider] and applies mute / speaker / hold / DTMF via the WebRTC stack.
 *
 * It implements the same [CallEngine] contract as the bundled
 * [com.chatapp.modern.engine.DemoCallEngine], so no UI changes are required.
 */
class WebRtcCallEngine(private val context: Context) : CallEngine, VideoProvider {

    private companion object {
        const val TAG = "WebRtcCallEngine"
    }

    override val stateObservers = CopyOnWriteArrayList<CallStateObserver>()
    private var _call: Call? = null
    override val currentCall: Call? get() = _call

    private val config: WebRtcConfig = WebRtcConfig.load(context)

    private var signaling: SignalingClient? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var pendingRemoteSink: VideoSink? = null
    private var pendingLocalSink: VideoSink? = null

    private var muted = false
    private var speaker = false
    private var held = false
    private var videoEnabled = false
    private var started = false

    private var peerAddress: String? = null
    private var pendingIncomingSdp: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // ---------------------------------------------------------------------
    // CallEngine
    // ---------------------------------------------------------------------

    override fun addStateObserver(observer: CallStateObserver) {
        stateObservers.add(observer)
        observer.onCallChanged(_call)
    }

    override fun removeStateObserver(observer: CallStateObserver) {
        stateObservers.remove(observer)
    }

    /** Register with the signaling server so incoming calls can reach us. */
    fun startListening() {
        if (started) return
        val cfg = WebRtcConfig.load(context)
        if (!cfg.useWebRtc || cfg.signalingServerUrl.isBlank()) {
            Log.w(TAG, "startListening skipped: WebRTC not configured")
            return
        }
        started = true
        ensureFactory()
        connectSignaling()
    }

    override fun startOutgoingCall(sipAddress: String, displayName: String?, videoEnabled: Boolean) {
        val cfg = WebRtcConfig.load(context)
        if (!cfg.useWebRtc || cfg.signalingServerUrl.isBlank()) {
            Log.e(TAG, "WebRTC not configured for outgoing call")
            return
        }
        val target = sipAddress.trim()
        ensureFactory()

        this.videoEnabled = videoEnabled && hasBackCamera()
        muted = false
        speaker = false
        held = false
        peerAddress = target

        publish(
            Call(
                callId = UUID.randomUUID().toString(),
                remoteSipAddress = target,
                remoteDisplayName = displayName ?: target,
                isOutgoing = true,
                videoEnabled = this.videoEnabled,
                state = CallState.RINGING
            )
        )

        ensureLocalMedia()
        mainHandler.postDelayed({ createAndSendOffer(target) }, 600)
    }

    override fun acceptIncomingCall() {
        val call = _call ?: return
        if (call.state != CallState.INCOMING) return
        val sdp = pendingIncomingSdp ?: return
        val from = peerAddress ?: return

        ensureLocalMedia()
        publish(call.copy(state = CallState.CONNECTING))
        val pc = ensurePeerConnection() ?: return

        pc.setRemoteDescription(incomingRemoteObserver(pc, from), SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    override fun declineIncomingCall() {
        peerAddress?.let { signaling?.send(Signal.bye(it)) }
        publish(_call?.copy(state = CallState.ENDED))
        mainHandler.postDelayed({ resetCall() }, 300)
    }

    override fun hangUp() {
        peerAddress?.let { signaling?.send(Signal.bye(it)) }
        publish(_call?.copy(state = CallState.ENDED))
        mainHandler.postDelayed({ resetCall() }, 300)
    }

    override fun isMicrophoneMuted(): Boolean = muted

    override fun toggleMicrophoneMute() {
        muted = !muted
        localAudioTrack?.setEnabled(!muted)
        notifyLoopback()
    }

    override fun isSpeakerEnabled(): Boolean = speaker

    override fun toggleSpeaker() {
        speaker = !speaker
        applyAudioRouting()
        notifyLoopback()
    }

    override fun isHeld(): Boolean = held

    override fun toggleHold() {
        val call = _call ?: return
        if (call.state != CallState.CONNECTED) return
        held = !held
        peerConnection?.transceivers?.forEach {
            it.direction = if (held) RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
            else RtpTransceiver.RtpTransceiverDirection.SEND_RECV
        }
        publish(call.copy(state = if (held) CallState.PAUSED else CallState.CONNECTED))
    }

    override fun isVideoEnabled(): Boolean = videoEnabled

    override fun toggleVideo() {
        videoEnabled = !videoEnabled
        localVideoTrack?.setEnabled(videoEnabled)
        if (videoEnabled && localVideoTrack == null) startCameraCapture()
        if (peerConnection?.signalingState() == PeerConnection.SignalingState.STABLE) {
            mainHandler.postDelayed({ renegotiateCall() }, 60)
        }
        notifyLoopback()
    }

    override fun sendDtmf(digit: Char) {
        peerAddress?.let { signaling?.send(Signal.dtmf(it, digit)) }
    }

    // ---------------------------------------------------------------------
    // VideoProvider
    // ---------------------------------------------------------------------

    override fun attachLocalSink(sink: VideoSink) {
        val track = localVideoTrack
        if (track != null) {
            track.addSink(sink)
        } else {
            pendingLocalSink = sink
        }
    }

    override fun attachRemoteSink(sink: VideoSink) {
        val track = remoteVideoTrack
        if (track != null) {
            track.addSink(sink)
        } else {
            pendingRemoteSink = sink
        }
    }

    override fun detachSinks() {
        pendingLocalSink = null
        pendingRemoteSink = null
        remoteVideoTrack?.removeSink(null)
        localVideoTrack?.removeSink(null)
    }

    // ---------------------------------------------------------------------
    // Factory + local media
    // ---------------------------------------------------------------------

    private fun ensureFactory() {
        if (factory != null) return
        val opts = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(opts)

        eglBase = EglBase.create()
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
            .createPeerConnectionFactory()

        applyAudioRouting()
    }

    private fun ensureLocalMedia() {
        if (audioSource == null) {
            audioSource = factory?.createAudioSource(MediaConstraints())
            localAudioTrack = factory?.createAudioTrack("audio0", audioSource)
        }
        if (videoEnabled && localVideoTrack == null) {
            startCameraCapture()
        }
    }

    private fun startCameraCapture() {
        try {
            val enumerator = Camera2Enumerator(context)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: return
            val capturer = enumerator.createCapturer(name, null)
            videoCapturer = capturer
            val st = SurfaceTextureHelper.create("VideoCapture", eglBase!!.eglBaseContext)
            surfaceTextureHelper = st
            val source = factory?.createVideoSource(capturer.isScreencast) ?: return
            capturer.initialize(st, context, source.capturerObserver)
            capturer.startCapture(640, 480, 30)
            localVideoTrack = factory?.createVideoTrack("video0", source)
            localVideoTrack?.let { track ->
                pendingLocalSink?.let { track.addSink(it) }
                pendingLocalSink = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera capture failed", e)
        }
    }

    private fun hasBackCamera(): Boolean = try {
        val names = Camera2Enumerator(context).deviceNames
        names.isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private fun ensurePeerConnection(): PeerConnection? {
        val f = factory ?: return null
        peerConnection?.let { return it }

        val iceServers = mutableListOf(
            PeerConnection.IceServer.builder(config.stunUrl).createIceServer()
        )
        config.turnUrl?.let { url ->
            iceServers.add(
                PeerConnection.IceServer.builder(url)
                    .setUsername(config.turnUsername ?: "")
                    .setPassword(config.turnCredential ?: "")
                    .createIceServer()
            )
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = f.createPeerConnection(rtcConfig, observer) ?: return null
        peerConnection = pc
        localAudioTrack?.let { pc.addTrack(it) }
        if (videoEnabled) localVideoTrack?.let { pc.addTrack(it) }
        return pc
    }

    // ---------------------------------------------------------------------
    // Signaling
    // ---------------------------------------------------------------------

    private fun connectSignaling() {
        val cfg = WebRtcConfig.load(context)
        val user = cfg.username.ifBlank { "device-${Build.MODEL.take(6)}" }
        signaling = SignalingClient(
            serverUrl = cfg.signalingServerUrl,
            userId = user,
            callbacks = object : SignalingClient.Callbacks {
                override fun onOpen() { Log.d(TAG, "Signaling open") }
                override fun onSignal(signal: Signal) = handleSignal(signal)
                override fun onError(reason: String) { Log.e(TAG, "Signaling error: $reason") }
                override fun onClosed() { Log.d(TAG, "Signaling closed") }
            }
        ).also { it.connect() }
    }

    private fun handleSignal(signal: Signal) {
        when (signal.type) {
            Signal.TYPE_OFFER -> onRemoteOffer(signal)
            Signal.TYPE_ANSWER -> onRemoteAnswer(signal)
            Signal.TYPE_CANDIDATE -> onRemoteCandidate(signal)
            Signal.TYPE_BYE -> onRemoteBye(signal)
            Signal.TYPE_DTMF -> Log.d(TAG, "DTMF from peer: ${signal.digit}")
        }
    }

    private fun onRemoteOffer(signal: Signal) {
        val caller = signal.from ?: return
        val active = _call?.state
        if (active == CallState.CONNECTED || active == CallState.CONNECTING) return

        peerAddress = caller
        pendingIncomingSdp = signal.sdp
        publish(
            Call(
                callId = UUID.randomUUID().toString(),
                remoteSipAddress = caller,
                remoteDisplayName = caller,
                isOutgoing = false,
                videoEnabled = videoEnabled,
                state = CallState.INCOMING
            )
        )
    }

    private fun onRemoteAnswer(signal: Signal) {
        val sdp = signal.sdp ?: return
        val pc = peerConnection ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {}
            override fun onSetSuccess() {
                if (_call?.state != CallState.CONNECTED) publish(_call?.copy(state = CallState.CONNECTED))
            }
            override fun onCreateFailure(reason: String) { onSdpError(reason) }
            override fun onSetFailure(reason: String) { onSdpError(reason) }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun onRemoteCandidate(signal: Signal) {
        val pc = peerConnection ?: return
        val sdp = signal.sdp ?: return
        pc.addIceCandidate(IceCandidate(signal.mid ?: "", signal.index, sdp))
    }

    private fun onRemoteBye(signal: Signal) {
        publish(_call?.copy(state = CallState.ENDED))
        mainHandler.postDelayed({ resetCall() }, 300)
    }

    // ---------------------------------------------------------------------
    // SDP offer/answer
    // ---------------------------------------------------------------------

    private fun createAndSendOffer(to: String) {
        val pc = ensurePeerConnection() ?: return
        val offerObserver = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
            }
            override fun onSetSuccess() {
                val s = pc.localDescription?.description ?: return
                signaling?.send(Signal.offer(to, s))
            }
            override fun onCreateFailure(reason: String) { onSdpError(reason) }
            override fun onSetFailure(reason: String) { onSdpError(reason) }
        }
        pc.createOffer(offerObserver, MediaConstraints())
    }

    /** Chain: answer for an incoming offer, then send the answer back. */
    private fun incomingRemoteObserver(pc: PeerConnection, from: String): SdpObserver =
        object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {}
            override fun onSetSuccess() {
                val answerObserver = object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription) {
                        pc.setLocalDescription(this, answer)
                    }
                    override fun onSetSuccess() {
                        val s = pc.localDescription?.description ?: return
                        signaling?.send(Signal.answer(from, s))
                        publish(_call?.copy(state = CallState.CONNECTED))
                    }
                    override fun onCreateFailure(reason: String) { onSdpError(reason) }
                    override fun onSetFailure(reason: String) { onSdpError(reason) }
                }
                pc.createAnswer(answerObserver, MediaConstraints())
            }
            override fun onCreateFailure(reason: String) { onSdpError(reason) }
            override fun onSetFailure(reason: String) { onSdpError(reason) }
        }

    private fun renegotiateCall() {
        val pc = peerConnection ?: return
        if (pc.signalingState() != PeerConnection.SignalingState.STABLE) return
        val offerObserver = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
            }
            override fun onSetSuccess() {
                val s = pc.localDescription?.description ?: return
                peerAddress?.let { signaling?.send(Signal.offer(it, s)) }
            }
            override fun onCreateFailure(reason: String) { Log.e(TAG, "renegotiate offer: $reason") }
            override fun onSetFailure(reason: String) { Log.e(TAG, "renegotiate set: $reason") }
        }
        pc.createOffer(offerObserver, MediaConstraints())
    }

    private fun resetCall() {
        peerConnection?.close()
        peerConnection = null
        remoteVideoTrack = null
        pendingRemoteSink = null
        pendingLocalSink = null
        pendingIncomingSdp = null
        peerAddress = null
        muted = false
        speaker = false
        held = false
        _call = null
        stateObservers.forEach { it.onCallChanged(null) }
    }

    // ---------------------------------------------------------------------
    // PeerConnection observer
    // ---------------------------------------------------------------------

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            if (newState == PeerConnection.SignalingState.STABLE && _call?.state == CallState.CONNECTING) {
                publish(_call?.copy(state = CallState.CONNECTED))
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            Log.d(TAG, "ICE: $newState")
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    if (_call?.state == CallState.CONNECTING) publish(_call?.copy(state = CallState.CONNECTED))
                }
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    if (_call != null) onRemoteBye(Signal(Signal.TYPE_BYE))
                }
                else -> {}
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}

        override fun onIceCandidate(candidate: IceCandidate) {
            peerAddress?.let { signaling?.send(Signal.candidate(it, candidate)) }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

        @Deprecated("Deprecated in favor of onTrack")
        override fun onAddStream(stream: MediaStream) {
            stream.videoTracks.firstOrNull()?.let { track ->
                remoteVideoTrack = track
                pendingRemoteSink?.let { track.addSink(it) }
            }
        }

        @Deprecated("Deprecated in favor of onTrack")
        override fun onRemoveStream(stream: MediaStream) {
            remoteVideoTrack = null
        }

        override fun onDataChannel(dataChannel: DataChannel) {}

        override fun onRenegotiationNeeded() {}

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            (receiver.track())?.let { track ->
                if (track is VideoTrack && remoteVideoTrack == null) {
                    remoteVideoTrack = track
                    pendingRemoteSink?.let { track.addSink(it) }
                }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack && remoteVideoTrack == null) {
                remoteVideoTrack = track
                pendingRemoteSink?.let { track.addSink(it) }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Audio routing
    // ---------------------------------------------------------------------

    private fun applyAudioRouting() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val target = if (speaker) {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                }
                if (target != null) audioManager.setCommunicationDevice(target)
            } else {
                audioManager.isSpeakerphoneOn = speaker
            }
        } catch (e: Exception) {
            Log.w(TAG, "Speaker routing failed", e)
        }
    }

    private fun notifyLoopback() {
        val call = _call ?: return
        publish(call.copy(callId = call.callId))
    }

    private fun publish(call: Call?) {
        _call = call
        stateObservers.forEach { it.onCallChanged(call) }
    }

    private fun onSdpError(reason: String) {
        Log.e(TAG, "SDP error: $reason")
        if (_call != null && _call?.state != CallState.ENDED) {
            publish(_call?.copy(state = CallState.ENDED))
        }
    }
}
