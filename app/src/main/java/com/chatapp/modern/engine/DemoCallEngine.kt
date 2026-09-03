package com.chatapp.modern.engine

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local, self-contained implementation of [CallEngine] that simulates a call
 * lifecycle using a main-thread [Handler]. It exists so the call UI is fully
 * interactive without a configured SIP account or any native library.
 *
 * When you integrate a real stack, create [CallEngine] impl and replace
 * [CallEngineLocator.engine] in your Application class.
 */
class DemoCallEngine : CallEngine {

    override val currentCall: Call?
        get() = _currentCall

    override val stateObservers: CopyOnWriteArrayList<CallStateObserver> =
        CopyOnWriteArrayList()

    @Volatile
    private var _currentCall: Call? = null

    private var muted = false
    private var speaker = false
    private var held = false
    private var videoEnabled = false

    private val handler = Handler(Looper.getMainLooper())

    private var transitionRunnable: Runnable? = null

    override fun addStateObserver(observer: CallStateObserver) {
        stateObservers.add(observer)
        observer.onCallChanged(_currentCall)
    }

    override fun removeStateObserver(observer: CallStateObserver) {
        stateObservers.remove(observer)
    }

    override fun startOutgoingCall(sipAddress: String, displayName: String?, videoEnabled: Boolean) {
        val trimmed = sipAddress.trim()
        if (trimmed.isEmpty()) return
        this.videoEnabled = videoEnabled
        muted = false
        speaker = false
        held = false

        val call = Call(
            callId = newCallId(),
            remoteSipAddress = trimSipScheme(trimmed),
            remoteDisplayName = displayName ?: trimSipScheme(trimmed),
            isOutgoing = true,
            videoEnabled = videoEnabled,
            state = CallState.RINGING
        )
        publish(call)

        // Simulated ringing -> connected after ~1.8 s.
        schedule(1800) {
            val current = _currentCall
            if (current?.callId == call.callId && current.state == CallState.RINGING) {
                publish(current.copy(state = CallState.CONNECTED))
            }
        }
    }

    override fun acceptIncomingCall() {
        val call = _currentCall ?: return
        if (call.state != CallState.INCOMING) return

        publish(call.copy(state = CallState.CONNECTING))
        schedule(700) {
            val current = _currentCall
            if (current?.callId == call.callId && current.state == CallState.CONNECTING) {
                publish(current.copy(state = CallState.CONNECTED))
            }
        }
    }

    override fun declineIncomingCall() {
        val call = _currentCall ?: return
        publish(call.copy(state = CallState.ENDED))
        schedule(400) { terminate() }
    }

    override fun hangUp() {
        val call = _currentCall ?: return
        publish(call.copy(state = CallState.ENDED))
        schedule(400) { terminate() }
    }

    override fun isMicrophoneMuted(): Boolean = muted

    override fun toggleMicrophoneMute() {
        muted = !muted
        notifyLoopback()
    }

    override fun isSpeakerEnabled(): Boolean = speaker

    override fun toggleSpeaker() {
        speaker = !speaker
        notifyLoopback()
    }

    override fun isHeld(): Boolean = held

    override fun toggleHold() {
        val call = _currentCall ?: return
        // Only meaningful while connected.
        if (call.state != CallState.CONNECTED) return
        held = !held
        publish(call.copy(state = if (held) CallState.PAUSED else CallState.CONNECTED))
    }

    override fun isVideoEnabled(): Boolean = videoEnabled

    override fun toggleVideo() {
        videoEnabled = !videoEnabled
        notifyLoopback()
    }

    override fun sendDtmf(digit: Char) {
        // In a real implementation this forwards the digit to the remote party.
        // Here we simply log / allow the keypad to reflect it.
        if (digit !in setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#')) return
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private fun notifyLoopback() {
        val call = _currentCall ?: return
        // Publish a copy so observers get a fresh object reference.
        publish(call.copy(callId = call.callId))
    }

    private fun publish(call: Call) {
        _currentCall = call
        stateObservers.forEach { it.onCallChanged(call) }
    }

    private fun terminate() {
        handler.removeCallbacksAndMessages(null)
        _currentCall = null
        stateObservers.forEach { it.onCallChanged(null) }
    }

    private fun schedule(delayMs: Long, block: () -> Unit) {
        handler.removeCallbacks(transitionRunnable)
        transitionRunnable = Runnable { block() }
        handler.postDelayed(transitionRunnable, delayMs)
    }

    private fun trimSipScheme(address: String): String =
        address
            .removePrefix("sip:")
            .removePrefix("SIP:")
            .substringBefore('@')
            .ifBlank { address }
}
