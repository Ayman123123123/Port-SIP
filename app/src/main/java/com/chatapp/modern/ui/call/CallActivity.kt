package com.chatapp.modern.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chatapp.modern.R
import com.chatapp.modern.databinding.ActivityCallBinding
import com.chatapp.modern.engine.Call
import com.chatapp.modern.engine.CallEngineLocator
import com.chatapp.modern.engine.CallState
import com.chatapp.modern.engine.CallStateObserver
import com.chatapp.modern.webrtc.VideoProvider
import org.webrtc.EglBase

/**
 * Active call screen: shows the remote party, call state and the standard in-call
 * controls (mute, hold, speaker, DTMF keypad, video, hang up).
 *
 * Launched by [com.chatapp.modern.ui.chat.ChatsFragment] when a call is initiated
 * from a conversation. If the active engine is a [VideoProvider] (WebRTC), local
 * and remote video renderers are attached and live video is shown.
 */
class CallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMOTE_ADDRESS = "extra_remote_address"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_OUTGOING = "extra_outgoing"
        const val EXTRA_VIDEO_ENABLED = "extra_video_enabled"
    }

    private lateinit var binding: ActivityCallBinding
    private val engine get() = CallEngineLocator.engine

    private var eglBase: EglBase? = null
    private var requestedOutgoing = false
    private var requestedAddress = ""
    private var requestedName: String? = null
    private var requestedVideo = false

    private var hasRenderedCall = false

    private val observer = CallStateObserver { call ->
        if (call == null) {
            if (hasRenderedCall) finish()
        } else {
            hasRenderedCall = true
            render(call)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startCallIfReady()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS).orEmpty()
        requestedName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        requestedOutgoing = intent.getBooleanExtra(EXTRA_OUTGOING, true)
        requestedVideo = intent.getBooleanExtra(EXTRA_VIDEO_ENABLED, false)

        binding.remoteName.text = requestedName?.takeIf { it.isNotBlank() } ?: requestedAddress
        binding.remoteNumber.text = requestedAddress
        binding.callState.text = getString(R.string.call_state_connecting)

        initVideoIfAvailable()

        engine.addStateObserver(observer)

        if (requiresPermissions()) {
            requestPermissions()
        } else {
            startCallIfReady()
        }

        wireControls()
    }

    private fun requiresPermissions(): Boolean {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val cam = requestedVideo &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        return mic != PackageManager.PERMISSION_GRANTED || cam
    }

    private fun requestPermissions() {
        val wanted = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.RECORD_AUDIO
        }
        if (requestedVideo &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.CAMERA
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun startCallIfReady() {
        if (requestedOutgoing) {
            engine.startOutgoingCall(requestedAddress, requestedName, requestedVideo)
        }
        // Incoming path: the engine already carries the call (WebRTC) or it's demo.
        val current = engine.currentCall
        if (current != null) render(current)
    }

    private fun initVideoIfAvailable() {
        val provider = engine as? VideoProvider ?: return
        eglBase = EglBase.create()
        val ctx = eglBase!!.eglBaseContext
        binding.remoteVideoRenderer.init(ctx, null)
        binding.localVideoRenderer.init(ctx, null)
        binding.localVideoRenderer.setMirror(true)
        provider.attachRemoteSink(binding.remoteVideoRenderer)
        provider.attachLocalSink(binding.localVideoRenderer)
    }

    private fun render(call: Call) {
        binding.remoteName.text = call.remoteDisplayName ?: call.remoteSipAddress
        binding.remoteNumber.text = call.remoteSipAddress
        binding.callState.text = when (call.state) {
            CallState.RINGING -> getString(R.string.call_state_ringing)
            CallState.INCOMING -> getString(R.string.call_state_incoming)
            CallState.CONNECTING -> getString(R.string.call_state_connecting)
            CallState.CONNECTED -> getString(R.string.call_state_connected)
            CallState.PAUSED -> getString(R.string.call_state_paused)
            CallState.ENDED -> getString(R.string.call_state_ended)
            CallState.IDLE -> getString(R.string.call_state_idle)
        }

        binding.remoteName.alpha = if (call.state == CallState.PAUSED) 0.4f else 1f

        binding.buttonMute.isSelected = engine.isMicrophoneMuted()
        binding.buttonSpeaker.isSelected = engine.isSpeakerEnabled()
        binding.buttonVideo.isSelected = engine.isVideoEnabled()

        updateVideoVisibility()
    }

    private fun updateVideoVisibility() {
        val isVideoProvider = engine is VideoProvider
        val show = isVideoProvider && engine.isVideoEnabled()
        binding.videoSurface.visibility = if (show) View.VISIBLE else View.GONE
        binding.videoHint.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun wireControls() {
        binding.buttonMute.setOnClickListener {
            engine.toggleMicrophoneMute()
            binding.buttonMute.isSelected = engine.isMicrophoneMuted()
        }

        binding.buttonSpeaker.setOnClickListener {
            engine.toggleSpeaker()
            binding.buttonSpeaker.isSelected = engine.isSpeakerEnabled()
        }

        binding.buttonHold.setOnClickListener {
            engine.toggleHold()
        }

        binding.buttonVideo.setOnClickListener {
            engine.toggleVideo()
            binding.buttonVideo.isSelected = engine.isVideoEnabled()
            updateVideoVisibility()
        }

        binding.buttonHangup.setOnClickListener {
            engine.hangUp()
            finish()
        }

        binding.buttonBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.buttonKeypad.setOnClickListener {
            binding.keypadContainer.visibility =
                if (binding.keypadContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        wireDtmfKeys()
    }

    private fun wireDtmfKeys() {
        val keys = listOf(
            Pair(binding.key0, '0'), Pair(binding.key1, '1'), Pair(binding.key2, '2'),
            Pair(binding.key3, '3'), Pair(binding.key4, '4'), Pair(binding.key5, '5'),
            Pair(binding.key6, '6'), Pair(binding.key7, '7'), Pair(binding.key8, '8'),
            Pair(binding.key9, '9'), Pair(binding.keyStar, '*'), Pair(binding.keyHash, '#')
        )
        keys.forEach { (view, digit) -> view.setOnClickListener { engine.sendDtmf(digit) } }
    }

    override fun onDestroy() {
        engine.removeStateObserver(observer)
        (engine as? VideoProvider)?.detachSinks()
        if (::binding.isInitialized) {
            binding.remoteVideoRenderer.release()
            binding.localVideoRenderer.release()
        }
        eglBase?.release()
        eglBase = null
        super.onDestroy()
    }
}
