package com.chatapp.modern.engine

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Abstraction over the actual telephony/SIP stack.
 *
 * The UI only talks to this interface. To go from the bundled [DemoCallEngine]
 * (a local simulation so the call screens are usable today) to a real SIP client
 * (e.g. [liblinphone](https://www.linphone.org) or another stack), you implement
 * this interface again and swap the instance in [com.chatapp.modern.engine.CallEngineLocator].
 *
 * All callbacks are delivered on the main thread.
 */
interface CallEngine {

    /** Current call, or null when idle. */
    val currentCall: Call?

    /** Observe state / call changes. */
    val stateObservers: CopyOnWriteArrayList<CallStateObserver>

    fun addStateObserver(observer: CallStateObserver)
    fun removeStateObserver(observer: CallStateObserver)

    /** Start an outgoing call to [sipAddress]. */
    fun startOutgoingCall(sipAddress: String, displayName: String? = null, videoEnabled: Boolean = false)

    /** Accept the incoming call. */
    fun acceptIncomingCall()

    /** Decline / reject the incoming call. */
    fun declineIncomingCall()

    /** Hang up the current call. */
    fun hangUp()

    fun isMicrophoneMuted(): Boolean
    fun toggleMicrophoneMute()

    fun isSpeakerEnabled(): Boolean
    fun toggleSpeaker()

    fun isHeld(): Boolean
    fun toggleHold()

    fun isVideoEnabled(): Boolean
    fun toggleVideo()

    /** Send one DTMF digit (0-9, *, #). */
    fun sendDtmf(digit: Char)
}

/** Observer notified on the main thread whenever the call model changes. */
fun interface CallStateObserver {
    fun onCallChanged(call: Call?)
}

/**
 * Supplies the active [CallEngine] so that screens can be wired against one
 * implementation and the implementation can be swapped later in one place.
 */
object CallEngineLocator {
    @Volatile
    var engine: CallEngine = DemoCallEngine()
}

internal fun newCallId(): String = UUID.randomUUID().toString()
