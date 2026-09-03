package com.chatapp.modern.webrtc

import org.json.JSONObject

/**
 * JSON wire protocol shared with the reference signaling server
 * (`server/signaling-server.js`). Messages are exchanged over a WebSocket after
 * joining a named room.
 *
 * Frame shape:
 *   {"type": "...", ...}
 *
 * Types:
 *   join        {room, user}                    client -> server
 *   joined      {room, peer}                    server -> caller  (a peer is present)
 *   offer       {sdp}                           client -> client
 *   answer      {sdp}                           client -> client
 *   candidate   {mid, index, sdp}               client -> client
 *   bye         {}                              client -> client
 *   dtmf        {digit}                         client -> client
 */
sealed class SignalingMessage {
    abstract val type: String

    data class Join(val room: String, val user: String) : SignalingMessage() {
        override val type = "join"
    }

    data class Sdp(val type: String, val sdp: String) : SignalingMessage() {
        override val type = type
    }

    data class Candidate(val mid: String?, val index: Int, val sdp: String) : SignalingMessage() {
        override val type = "candidate"
    }

    object Bye : SignalingMessage() {
        override val type = "bye"
    }

    data class Dtmf(val digit: Char) : SignalingMessage() {
        override val type = "dtmf"
    }

    fun toJson(): JSONObject = when (this) {
        is Join -> JSONObject().put("type", type).put("room", room).put("user", user)
        is Sdp -> JSONObject().put("type", type).put("sdp", sdp)
        is Candidate -> JSONObject()
            .put("type", type)
            .put("mid", mid)
            .put("index", index)
            .put("sdp", sdp)
        Bye -> JSONObject().put("type", type)
        is Dtmf -> JSONObject().put("type", type).put("digit", digit.toString())
    }

    fun encode(): String = toJson().toString()

    companion object {
        fun decode(json: String): SignalingMessage? = try {
            val obj = JSONObject(json)
            when (obj.getString("type")) {
                "join" -> Join(obj.getString("room"), obj.optString("user"))
                "offer" -> Sdp("offer", obj.getString("sdp"))
                "answer" -> Sdp("answer", obj.getString("sdp"))
                "candidate" -> Candidate(obj.optString("mid").ifEmpty { null }, obj.optInt("index"), obj.getString("sdp"))
                "bye" -> Bye
                "dtmf" -> Dtmf(obj.optString("digit").firstOrNull() ?: '0')
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
