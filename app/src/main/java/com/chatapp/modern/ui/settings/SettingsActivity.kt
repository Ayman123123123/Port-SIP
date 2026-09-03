package com.chatapp.modern.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chatapp.modern.R
import com.chatapp.modern.databinding.ActivitySettingsBinding
import com.chatapp.modern.engine.CallEngineLocator
import com.chatapp.modern.webrtc.WebRtcConfig
import com.chatapp.modern.webrtc.WebRtcCallEngine

/**
 * Configuration for the real-time (WebRTC) call engine.
 *
 * Fill in the signaling server URL and your username, then enable "Use WebRTC".
 * The settings are persisted in [WebRtcConfig] and applied on the next call. For
 * incoming calls, the app registers with the signaling server on startup.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val config = WebRtcConfig.load(this)
        bind(config)

        binding.buttonBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.buttonSave.setOnClickListener {
            val updated = WebRtcConfig(
                signalingServerUrl = binding.signalingServer.text.toString().trim(),
                username = binding.username.text.toString().trim(),
                stunUrl = binding.stun.text.toString().trim().ifBlank { WebRtcConfig.DEFAULT_STUN },
                turnUrl = binding.turnUrl.text.toString().trim().ifBlank { null },
                turnUsername = binding.turnUsername.text.toString().trim().ifBlank { null },
                turnCredential = binding.turnCredential.text.toString().trim().ifBlank { null },
                useWebRtc = binding.useWebRtc.isChecked
            )
            WebRtcConfig.save(this, updated)

            // Re-resolve the engine so the next call uses the new configuration.
            CallEngineLocator.refresh()
            val engine = CallEngineLocator.engine
            if (engine is WebRtcCallEngine) engine.startListening()

            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bind(config: WebRtcConfig) {
        binding.signalingServer.setText(config.signalingServerUrl)
        binding.username.setText(config.username)
        binding.stun.setText(config.stunUrl)
        binding.turnUrl.setText(config.turnUrl ?: "")
        binding.turnUsername.setText(config.turnUsername ?: "")
        binding.turnCredential.setText(config.turnCredential ?: "")
        binding.useWebRtc.isChecked = config.useWebRtc
    }
}
