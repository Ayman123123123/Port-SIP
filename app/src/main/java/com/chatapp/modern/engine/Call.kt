package com.chatapp.modern.engine

/**
 * Immutable snapshot of the current call, exposed to the UI layer.
 *
 * This model is intentionally UI-agnostic: it describes the remote party and the
 * call properties, but knows nothing about how the call is actually established.
 * That separation is what allows the [CallEngine] implementation to be swapped
 * (demo vs. a real SIP stack) without touching any screen.
 */
data class Call(
    val callId: String,
    val remoteSipAddress: String,
    val remoteDisplayName: String?,
    val isOutgoing: Boolean,
    val videoEnabled: Boolean,
    val state: CallState
)

/**
 * The lifecycle states a call may go through.
 */
enum class CallState {
    IDLE,
    RINGING,     // outgoing: waiting for the remote party to answer
    INCOMING,    // incoming: we are being called
    CONNECTING,  // negotiation / early media
    CONNECTED,   // call is up
    PAUSED,      // held
    ENDED
}
