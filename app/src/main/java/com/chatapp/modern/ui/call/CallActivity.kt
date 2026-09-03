package com.chatapp.modern.ui.call

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.chatapp.modern.R
import com.chatapp.modern.databinding.ActivityCallBinding
import com.chatapp.modern.engine.Call
import com.chatapp.modern.engine.CallEngineLocator
import com.chatapp.modern.engine.CallState
import com.chatapp.modern.engine.CallStateObserver

/**
 * Active call screen: shows the remote party, call state and the standard in-call
 * controls (mute, hold, speaker, DTMF keypad, video, hang up).
 *
 * This is the screen the [com.chatapp.modern.ui.chat.ChatsFragment] launches when a
 * call is initiated from a conversation.
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

    // Only finish when a call that was displayed actually ends (transitions to
    // null); a null at registration time (fresh outgoing call) must not close us.
    private var hasRenderedCall = false

    private val observer = CallStateObserver { call ->
        if (call == null) {
            if (hasRenderedCall) finish()
        } else {
            hasRenderedCall = true
            render(call)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val remoteAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS).orEmpty()
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val outgoing = intent.getBooleanExtra(EXTRA_OUTGOING, true)
        val video = intent.getBooleanExtra(EXTRA_VIDEO_ENABLED, false)

        binding.remoteName.text = displayName?.ifBlank { null } ?: remoteAddress
        binding.remoteNumber.text = remoteAddress
        binding.callState.text = getString(R.string.call_state_connecting)

        engine.addStateObserver(observer)

        if (outgoing) {
            engine.startOutgoingCall(remoteAddress, displayName, video)
        }
        // For the incoming-then-accepted path the engine already carries the call;
        // the observer above will render it.

        wireControls()
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

        binding.videoSurface.visibility = if (engine.isVideoEnabled()) View.VISIBLE else View.GONE
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
            binding.videoSurface.visibility = if (engine.isVideoEnabled()) View.VISIBLE else View.GONE
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
        super.onDestroy()
    }
}
