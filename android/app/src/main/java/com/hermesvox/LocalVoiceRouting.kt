package com.hermesvox

import java.util.Locale

/** The two English commands that can run locally after a confirmed voice turn. */
object LocalVoiceRouting {
    const val PREF = "local_voice_routing"
    const val DEFAULT_ENABLED = false
    internal const val MINUTE_MS = 60_000L

    private val timer = Regex("set a timer for (.+) minutes?")
    private val volume = Regex("set (?:the )?(?:media )?volume to (.+?)(?: percent| ?%)")
    private val spacing = Regex("[ \\t]+")
    private val athenaAddress = Regex("^athena(?:, ?| )")
    private val commandStart = Regex("^(?:athena|set)(?=[\\s,.!?]|$)", RegexOption.IGNORE_CASE)
    private val digits = Regex("[0-9]+")
    private val smallNumbers = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen",
    )
    private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** A recognizer can add punctuation before the caller finishes the command. */
    fun needsFinalTranscript(partial: String): Boolean = commandStart.containsMatchIn(partial.trimStart())

    fun phoneArguments(transcript: String): Map<String, Any?>? {
        val command = transcript.trim().removeSuffix(".").lowercase(Locale.ROOT)
            .replace(spacing, " ").replaceFirst(athenaAddress, "")
        timer.matchEntire(command)?.let { match ->
            val minutes = number(match.groupValues[1]) ?: return null
            if (minutes !in 1..(PhoneTool.MAX_DURATION_MS / MINUTE_MS).toInt()) return null
            return mapOf("action" to "timer", "duration_ms" to minutes * MINUTE_MS)
        }
        volume.matchEntire(command)?.let { match ->
            val percent = number(match.groupValues[1]) ?: return null
            if (percent !in 0..100) return null
            return mapOf("action" to "volume", "level" to percent)
        }
        return null
    }

    private fun number(text: String): Int? {
        if (digits.matches(text)) return text.toIntOrNull()
        smallNumbers.indexOf(text).takeIf { it >= 0 }?.let { return it }
        if (text == "one hundred" || text == "a hundred") return 100
        val words = text.split(' ', '-', limit = 2)
        val ten = tens.indexOf(words[0]).takeIf { it >= 0 } ?: return null
        val unit = if (words.size == 1) 0 else smallNumbers.indexOf(words[1]).takeIf { it in 1..9 } ?: return null
        return (ten + 2) * 10 + unit
    }
}
