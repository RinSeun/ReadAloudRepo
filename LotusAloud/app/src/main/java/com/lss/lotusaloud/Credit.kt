package com.lss.lotusaloud

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Your Fish Audio API credit balance. */
object Credit {
    const val LOW = 1.0 // warn below this

    @Volatile var lastValue: Double? = null
    @Volatile var lastChecked = 0L

    /** Returns the balance, or throws with a short friendly message. Runs on a background thread. */
    fun fetch(apiKey: String): Double {
        val c = URL("https://api.fish.audio/wallet/self/api-credit").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            val code = c.responseCode
            if (code == 401 || code == 403) throw Exception("Key not accepted")
            if (code != 200) throw Exception("Couldn't check ($code)")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            // the balance may come back as a number or as text
            val v = o.opt("credit")
            val credit = when (v) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            } ?: throw Exception("Couldn't read balance")
            lastValue = credit
            lastChecked = System.currentTimeMillis()
            return credit
        } catch (e: java.io.IOException) {
            throw Exception("Offline")
        } finally {
            c.disconnect()
        }
    }

    fun format(v: Double): String = "$" + String.format("%.2f", v)
}
