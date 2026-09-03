package com.chatapp.modern.webrtc

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket signaling client.
 *
 * [connect] opens a socket and immediately registers the local [userId] so the
 * server can route incoming calls. The engine drives the dialogue through [send].
 */
class SignalingClient(
    private val serverUrl: String,
    private val userId: String,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onOpen()
        fun onSignal(signal: Signal)
        fun onError(reason: String)
        fun onClosed()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // keep alive for the whole call / presence
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    send(Signal.register(userId))
                    dispatch { callbacks.onOpen() }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val signal = Signal.decode(text)
                    if (signal != null && signal.type == Signal.TYPE_ERROR) {
                        dispatch { callbacks.onError(signal.reason ?: "Signaling error") }
                    } else if (signal != null) {
                        dispatch { callbacks.onSignal(signal) }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    dispatch { callbacks.onError(t.message ?: "Connection error") }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    dispatch { callbacks.onClosed() }
                }
            })
        } catch (e: Exception) {
            dispatch { callbacks.onError(e.message ?: "Unable to open signaling socket") }
        }
    }

    /** Send a [Signal] frame; safe to call from any thread. */
    fun send(signal: Signal) {
        webSocket?.send(signal.encode())
    }

    fun close() {
        webSocket?.close(1000, "call ended")
        webSocket = null
    }

    private fun dispatch(runnable: () -> Unit) {
        mainHandler.post(runnable)
    }
}
