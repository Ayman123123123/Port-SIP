package com.chatapp.modern.webrtc

import org.json.JSONObject

/**
 * Signaling frame shared between client and the reference signaling server
 * (`server/signaling-server.js`).
 *
 * The server keeps a registry of currently-online users (keyed by their
 * registered `user` id). To call someone, a client sends a message with a `to`
 * target; the server resolves that target to an open socket, sets `from` to the
 * sender's id, and relays it. A receiver uses `from` as the peer address to reply.
 *
 * Types:
 *   register    {user}                     client -> server  (presence)
 *   offer       {to, from, sdp}            routed
 *   answer      {to, from, sdp}            routed
 *   candidate   {to, from, mid, index, sdp} routed
 *   bye         {to, from}                 routed
 *   dtmf        {to, from, digit}          routed
 *   error       {reason}                   server -> client
 */
data class Signal(
    val type: String,
    val to: String? = null,
    val from: String? = null,
    val sdp: String? = null,
    val mid: String? = null,
    val index: Int = 0,
    val digit: String? = null,
    val user: String? = null,
    val reason: String? = null
) {
    fun encode(): String = JSONObject().apply {
        put("type", type)
        to?.let { put("to", it) }
        from?.let { put("from", it) }
        sdp?.let { put("sdp", it) }
        mid?.let { put("mid", it) }
        if (type == TYPE_CANDIDATE) put("index", index)
        digit?.let { put("digit", it) }
        user?.let { put("user", it) }
        reason?.let { put("reason", it) }
    }.toString()

    companion object {
        const val TYPE_REGISTER = "register"
        const val TYPE_OFFER = "offer"
        const val TYPE_ANSWER = "answer"
        const val TYPE_CANDIDATE = "candidate"
        const val TYPE_BYE = "bye"
        const val TYPE_DTMF = "dtmf"
        const val TYPE_ERROR = "error"

        fun register(user: String) = Signal(TYPE_REGISTER, user = user)
        fun offer(to: String, sdp: String) = Signal(TYPE_OFFER, to = to, sdp = sdp)
        fun answer(to: String, sdp: String) = Signal(TYPE_ANSWER, to = to, sdp = sdp)
        fun candidate(to: String, c: org.webrtc.IceCandidate) =
            Signal(TYPE_CANDIDATE, to = to, mid = c.sdpMid, index = c.sdpMLineIndex, sdp = c.sdp)
        fun bye(to: String) = Signal(TYPE_BYE, to = to)
        fun dtmf(to: String, digit: Char) = Signal(TYPE_DTMF, to = to, digit = digit.toString())

        fun decode(json: String): Signal? = try {
            val o = JSONObject(json)
            Signal(
                type = o.getString("type"),
                to = o.optString("to").ifEmpty { null },
                from = o.optString("from").ifEmpty { null },
                sdp = o.optString("sdp").ifEmpty { null },
                mid = o.optString("mid").ifEmpty { null },
                index = o.optInt("index", 0),
                digit = o.optString("digit").ifEmpty { null },
                user = o.optString("user").ifEmpty { null },
                reason = o.optString("reason").ifEmpty { null }
            )
        } catch (e: Exception) {
            null
        }
    }
}
