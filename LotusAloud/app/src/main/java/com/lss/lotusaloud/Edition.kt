package com.lss.lotusaloud

object Edition {
    val PLAY: Boolean = BuildConfig.PLAY
    val TAGLINE: String = "Made by Seung-jae"
    val DEFAULT_VOICE: String = BuildConfig.DEFAULT_VOICE
    val SUPPORT_EMAIL: String = BuildConfig.SUPPORT_EMAIL.trim()
    val PRIVACY_URL: String = BuildConfig.PRIVACY_URL.trim()
    val REPORT_WEBHOOK: String = BuildConfig.REPORT_WEBHOOK.trim()
    val DEFAULT_VOICE_NAME: String = "Phone voice"
    const val TERMS_URL = "https://lotusshieldstudios.com/terms"
    const val TERMS_VERSION = 1
    fun needsAgreement(ctx: android.content.Context) = Prefs(ctx).termsAccepted < TERMS_VERSION

    fun askToAgree(a: android.app.Activity) {
        android.widget.Toast.makeText(a, "Open Lotus Aloud once to get started.", android.widget.Toast.LENGTH_LONG).show()
        a.startActivity(android.content.Intent(a, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        a.finish()
    }

    val PRIVACY_PAGE: String get() = PRIVACY_URL.ifBlank { "https://lotusshieldstudios.com/privacy" }
}
