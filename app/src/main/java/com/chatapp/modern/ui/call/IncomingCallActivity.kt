package com.chatapp.modern.ui.call

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chatapp.modern.R
import com.chatapp.modern.databinding.ActivityIncomingCallBinding
import com.chatapp.modern.engine.Call
import com.chatapp.modern.engine.CallEngineLocator
import com.chatapp.modern.engine.CallStateObserver

/**
 * Incoming call screen: shows the caller identity and Accept / Decline actions.
 *
 * Kept outside the call screen so it can ring over the lockscreen; it is wired to
 * the same shared [com.chatapp.modern.engine.CallEngine].
 */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private val engine get() = CallEngineLocator.engine

    private var hasRenderedCall = false

    private val observer = CallStateObserver { call ->
        if (call == null) {
            if (hasRenderedCall) finish()
        } else {
            hasRenderedCall = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine.addStateObserver(observer)
        render(engine.currentCall)

        binding.actionDecline.setOnClickListener {
            engine.declineIncomingCall()
            finish()
        }

        binding.actionAnswer.setOnClickListener {
            engine.acceptIncomingCall()
            openCallScreen()
        }
    }

    private fun render(call: Call?) {
        if (call == null) {
            finish()
            return
        }
        binding.callerName.text = call.remoteDisplayName ?: call.remoteSipAddress
        binding.callerNumber.text = call.remoteSipAddress
        binding.callStateText.text = getString(R.string.incoming_call_text)
    }

    private fun openCallScreen() {
        val call = engine.currentCall
        val intent = android.content.Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, call?.remoteSipAddress ?: "")
            putExtra(CallActivity.EXTRA_DISPLAY_NAME, call?.remoteDisplayName)
            putExtra(CallActivity.EXTRA_OUTGOING, false)
            putExtra(CallActivity.EXTRA_VIDEO_ENABLED, call?.videoEnabled ?: false)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        engine.removeStateObserver(observer)
        super.onDestroy()
    }
}
