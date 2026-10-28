package com.lss.lotusaloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val text: String, val voice: String, val time: Long)

/** Your recent reads, newest first. Replaying one is instant and free (the audio is kept on the phone). */
object History {
    private const val MAX = 15
    private const val KEY = "history"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("fish_reader", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<HistoryItem> = try {
        val arr = JSONArray(sp(ctx).getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryItem(o.optString("text"), o.optString("voice"), o.optLong("time"))
        }.filter { it.text.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(ctx: Context, text: String, voice: String) {
        val items = list(ctx).filterNot { it.text == text && it.voice == voice }.toMutableList()
        items.add(0, HistoryItem(text.take(20000), voice, System.currentTimeMillis()))
        save(ctx, items.take(MAX))
    }

    fun clear(ctx: Context) = save(ctx, emptyList())

    private fun save(ctx: Context, items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("text", it.text).put("voice", it.voice).put("time", it.time)) }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
