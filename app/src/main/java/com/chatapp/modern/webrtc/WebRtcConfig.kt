package com.chatapp.modern.webrtc

import android.content.Context

/**
 * Persisted configuration for the WebRTC call engine.
 *
 * Values are stored in [SharedPreferences] and read back on each engine creation,
 * so changing settings takes effect on the next call (no app restart required).
 */
data class WebRtcConfig(
    val signalingServerUrl: String,
    val username: String,
    val stunUrl: String,
    val turnUrl: String?,
    val turnUsername: String?,
    val turnCredential: String?,
    /** When false, the app uses the bundled [com.chatapp.modern.engine.DemoCallEngine]. */
    val useWebRtc: Boolean
) {
    companion object {
        private const val PREFS = "webrtc_config"
        private const val KEY_URL = "signaling_server_url"
        private const val KEY_USER = "username"
        private const val KEY_STUN = "stun_url"
        private const val KEY_TURN_URL = "turn_url"
        private const val KEY_TURN_USER = "turn_username"
        private const val KEY_TURN_PASS = "turn_credential"
        private const val KEY_ENABLED = "use_webrtc"

        const val DEFAULT_STUN = "stun:stun.l.google.com:19302"

        fun load(context: Context): WebRtcConfig {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WebRtcConfig(
                signalingServerUrl = p.getString(KEY_URL, "") ?: "",
                username = p.getString(KEY_USER, "") ?: "",
                stunUrl = p.getString(KEY_STUN, DEFAULT_STUN) ?: DEFAULT_STUN,
                turnUrl = p.getString(KEY_TURN_URL, null),
                turnUsername = p.getString(KEY_TURN_USER, null),
                turnCredential = p.getString(KEY_TURN_PASS, null),
                useWebRtc = p.getBoolean(KEY_ENABLED, false)
            )
        }

        fun save(context: Context, config: WebRtcConfig) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_URL, config.signalingServerUrl)
                .putString(KEY_USER, config.username)
                .putString(KEY_STUN, config.stunUrl)
                .putString(KEY_TURN_URL, config.turnUrl)
                .putString(KEY_TURN_USER, config.turnUsername)
                .putString(KEY_TURN_PASS, config.turnCredential)
                .putBoolean(KEY_ENABLED, config.useWebRtc)
                .apply()
        }

        /** True when the config points at a usable real-time setup. */
        fun isReady(config: WebRtcConfig): Boolean =
            config.useWebRtc && config.signalingServerUrl.isNotBlank()
    }
}
