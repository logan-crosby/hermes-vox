package com.hermesvox

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

internal object AthenaSpeechCheck {
    private val busy = AtomicBoolean(false)

    fun run(context: Context): JSONObject {
        val report = JSONObject()
        if (MainActivity.callIsLive() || MainActivity.callStartPending || !busy.compareAndSet(false, true)) {
            return report.put("speech_check", "busy")
        }
        val voice = SherpaTts(context, SherpaVoice.PIPER)
        val hearing = OfflineWhisperStt(context, ModelCatalog.DEFAULT_STT_MODEL)
        val vad = SileroVadGate(context)
        try {
            fun ready(start: ((Boolean) -> Unit) -> Unit): Boolean {
                val done = CompletableFuture<Boolean>()
                start { done.complete(it) }
                return done.get()
            }
            if (!ready(voice::init)) return report.put("speech_check", "piper_init_failed")
            if (!ready(vad::init)) return report.put("speech_check", "vad_init_failed")
            if (!ready(hearing::init)) return report.put("speech_check", "whisper_init_failed")
            val expected = mapOf("action" to "timer", "duration_ms" to 300_000L)
            var passed = true
            for ((name, phrase) in listOf("plain" to "Set a timer for five minutes.",
                                         "addressed" to "Athena, set a timer for five minutes.")) {
                val audio = voice.renderSamples(phrase)
                val samples = audio.samples ?: return report.put("speech_check", "no_audio")
                val pcm = hearing.resample(samples, audio.sampleRate, 16000)
                vad.reset()
                val detected = (0..(pcm.size - 512) step 512).any { vad.feed(pcm.copyOfRange(it, it + 512)) }
                val transcript = hearing.transcribeWhole(pcm, 16000).orEmpty()
                val matched = LocalVoiceRouting.phoneArguments(transcript) == expected
                report.put(name, JSONObject().put("generated_samples", samples.size)
                    .put("speech_detected", detected).put("fixture_transcript", transcript.take(160))
                    .put("five_minute_timer_recognized", matched))
                if (!detected || !matched) passed = false
            }
            return report.put("speech_check", if (passed) "ok" else "mismatch")
        } finally {
            vad.shutdown()
            hearing.shutdown()
            voice.shutdown()
            busy.set(false)
        }
    }
}
