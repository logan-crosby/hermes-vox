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
            val audio = voice.renderSamples("Athena, set a timer for five minutes.")
            val samples = audio.samples ?: return report.put("speech_check", "no_audio")
            report.put("generated_samples", samples.size)
            if (!ready(vad::init)) return report.put("speech_check", "vad_init_failed")
            val pcm = hearing.resample(samples, audio.sampleRate, 16000)
            val detected = (0..(pcm.size - 512) step 512).any { vad.feed(pcm.copyOfRange(it, it + 512)) }
            report.put("speech_detected", detected)
            if (!ready(hearing::init)) return report.put("speech_check", "whisper_init_failed")
            val transcript = hearing.transcribe(pcm, 16000).orEmpty()
            val expected = LocalVoiceRouting.phoneArguments("set a timer for five minutes")
            val matched = LocalVoiceRouting.phoneArguments(transcript) == expected
            report.put("five_minute_timer_recognized", matched)
            return report.put("speech_check", if (detected && matched) "ok" else "mismatch")
        } finally {
            vad.shutdown()
            hearing.shutdown()
            voice.shutdown()
            busy.set(false)
        }
    }
}
