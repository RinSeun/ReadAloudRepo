package com.lss.lotusaloud

object Edition {
    val PLAY: Boolean = BuildConfig.PLAY
    val TAGLINE: String = if (PLAY) "by Lotus Shield Studio" else "Made for LSS Only"
    val DEFAULT_VOICE: String = BuildConfig.DEFAULT_VOICE
    val SUPPORT_EMAIL: String = BuildConfig.SUPPORT_EMAIL.trim()
    val PRIVACY_URL: String = BuildConfig.PRIVACY_URL.trim()
    val REPORT_WEBHOOK: String = BuildConfig.REPORT_WEBHOOK.trim()
    val DEFAULT_VOICE_NAME: String = if (PLAY) "Phone voice" else "My voice"
}
