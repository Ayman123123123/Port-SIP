package com.chatapp.modern

import android.app.Application
import com.chatapp.modern.engine.CallEngineLocator

/**
 * Application entry point.
 *
 * This is the single place to swap the call engine. The bundled default is
 * [com.chatapp.modern.engine.DemoCallEngine] so the UI runs without a SIP account.
 * When you integrate a real stack, set [CallEngineLocator.engine] here.
 */
class PortSipApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Kept explicit and easy to find; CallEngineLocator already defaults to DemoCallEngine.
        checkNotNull(CallEngineLocator.engine) { "CallEngineLocator.engine must not be null" }
    }
}
