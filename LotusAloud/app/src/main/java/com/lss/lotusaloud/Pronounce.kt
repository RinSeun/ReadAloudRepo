package com.lss.lotusaloud

import android.content.Context

data class SayRule(val word: String, val sayAs: String)

object Pronounce {
    @Volatile private var compiled: List<Pair<Regex, String>> = emptyList()
    @Volatile private var loaded = false

    fun load(ctx: Context) {
        set(Prefs(ctx).sayRules)
    }

    fun set(rules: List<SayRule>) {
        compiled = rules.filter { it.word.isNotBlank() }
            .sortedByDescending { it.word.length }
            .map { r ->
                Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(r.word.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) to
                    Regex.escapeReplacement(r.sayAs)
            }
        loaded = true
    }

    fun apply(text: String): String {
        var t = text
        for ((re, to) in compiled) t = re.replace(t, to)
        return t
    }
}
