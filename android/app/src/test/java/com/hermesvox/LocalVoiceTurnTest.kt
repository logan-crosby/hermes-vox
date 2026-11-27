package com.hermesvox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVoiceTurnTest {

    @Test fun typed_turns_wait_for_the_attempted_local_action_and_keep_fifo_order() {
        val turn = LocalVoiceTurn()
        turn.begin(21L)
        assertTrue(turn.active(21L))
        assertTrue(turn.deferTyped(21L, "first", pasted = false, display = "First"))
        assertTrue(turn.deferTyped(21L, "second", pasted = true, display = "Second"))

        assertNull("queued input cannot take the active action's turn", turn.takeTyped())
        turn.finish(20L)
        assertTrue("a stale release cannot end the local action", turn.active(21L))
        assertNull("queued input remains held", turn.takeTyped())

        turn.finish(21L)
        assertFalse(turn.active(21L))
        assertEquals(LocalVoiceTurn.Typed("first", false, "First"), turn.takeTyped())
        assertEquals(LocalVoiceTurn.Typed("second", true, "Second"), turn.takeTyped())
        assertNull("each typed turn is delivered once", turn.takeTyped())
    }
}
