package com.hermesvox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LocalVoiceRoutingTest {

    @Test fun timer_arguments_preserve_exact_minutes_and_inclusive_bounds() {
        assertEquals(
            mapOf("action" to "timer", "duration_ms" to 60_000L),
            LocalVoiceRouting.phoneArguments("Set a timer for one minute"),
        )
        assertEquals(
            mapOf("action" to "timer", "duration_ms" to 5 * 60_000L),
            LocalVoiceRouting.phoneArguments("set a timer for 5 minutes."),
        )
        assertEquals(
            mapOf("action" to "timer", "duration_ms" to 86_400_000L),
            LocalVoiceRouting.phoneArguments("set a timer for 1440 minutes"),
        )
        assertNull(LocalVoiceRouting.phoneArguments("set a timer for 0 minutes"))
        assertNull(LocalVoiceRouting.phoneArguments("set a timer for 1441 minutes"))
    }

    @Test fun volume_arguments_preserve_absolute_percent_and_inclusive_bounds() {
        assertEquals(
            mapOf("action" to "volume", "level" to 37),
            LocalVoiceRouting.phoneArguments("SET THE MEDIA VOLUME TO 37 PERCENT."),
        )
        assertEquals(
            mapOf("action" to "volume", "level" to 0),
            LocalVoiceRouting.phoneArguments("set volume to zero percent"),
        )
        assertEquals(
            mapOf("action" to "volume", "level" to 100),
            LocalVoiceRouting.phoneArguments("set volume to 100%"),
        )
        assertNull(LocalVoiceRouting.phoneArguments("set volume to 101 percent"))
    }

    @Test fun only_complete_unambiguous_commands_route_locally() {
        listOf(
            "Should I set a timer for 5 minutes?",
            "Don't set a timer for 5 minutes",
            "set a timer for 5 minutes and set volume to 40 percent",
            "set volume to 40 percent, then set a timer for 5 minutes",
            "remember to set a timer for 5 minutes",
            "set a timer for 5 minutes and remember this",
            "set volume to 40 percent please",
            "set volume to 40 percent?",
        ).forEach { command ->
            assertNull("must defer: $command", LocalVoiceRouting.phoneArguments(command))
        }
    }

    @Test fun local_routing_is_disabled_by_default() {
        assertFalse(LocalVoiceRouting.DEFAULT_ENABLED)
        assertEquals("local_voice_routing", LocalVoiceRouting.PREF)
    }

    @Test fun athena_can_be_addressed_once_before_a_complete_command() {
        val timer = LocalVoiceRouting.phoneArguments("set a timer for five minutes")
        val volume = LocalVoiceRouting.phoneArguments("set volume to 30 percent")
        assertEquals(timer, LocalVoiceRouting.phoneArguments("Athena, set a timer for five minutes."))
        assertEquals(volume, LocalVoiceRouting.phoneArguments("ATHENA set volume to 30 percent"))
        for (phrase in listOf(
            "Ask Athena to set a timer for five minutes",
            "Athena, remember to set a timer for five minutes",
            "Athena, don't set volume to 30 percent",
            "Athena, set volume to 30 percent and tell me what you remember",
            "Athena, set volume to 30 percent?",
            "Athenaset volume to 30 percent",
            "Athena, Athena, set volume to 30 percent",
        )) assertNull(phrase, LocalVoiceRouting.phoneArguments(phrase))
    }

    @Test fun a_punctuated_partial_waits_for_the_final_decode() {
        for (partial in listOf("Athena.", " ATHENA,", "Set a timer.", "set volume to thirty.", "Set")) {
            assertTrue(partial, LocalVoiceRouting.needsFinalTranscript(partial))
            assertNull(partial, LocalVoiceRouting.phoneArguments(partial))
        }
        for (ordinary in listOf("What is next?", "Settings are open.", "Athenaeum.")) {
            assertFalse(ordinary, LocalVoiceRouting.needsFinalTranscript(ordinary))
        }
    }
}
