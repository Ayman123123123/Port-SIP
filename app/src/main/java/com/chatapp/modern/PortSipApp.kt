package com.chatapp.modern

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.chatapp.modern.engine.CallEngineLocator
import com.chatapp.modern.engine.CallState
import com.chatapp.modern.ui.call.IncomingCallActivity
import com.chatapp.modern.webrtc.WebRtcCallEngine

/**
 * Application entry point.
 *
 * Initializes the call engine (demo or WebRTC based on configuration) and, when
 * WebRTC is live, registers the user with the signaling server so incoming calls
 * arrive and launch the [IncomingCallActivity].
 */
class PortSipApp : Application() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        CallEngineLocator.init(this)

        val engine = CallEngineLocator.engine
        if (engine is WebRtcCallEngine) {
            engine.startListening()
            engine.addStateObserver { call ->
                if (call != null &&
                    call.state == CallState.INCOMING &&
                    !IncomingCallActivity.isOpen() &&
                    !IncomingCallActivity.isPendingLaunch()
                ) {
                    IncomingCallActivity.markPendingLaunch()
                    handler.post {
                        val intent = Intent(this, IncomingCallActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
