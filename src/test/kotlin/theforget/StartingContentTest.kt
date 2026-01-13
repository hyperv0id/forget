package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartingContentTest {
    @Test
    fun `starting deck contains only Strike_R and Defend_R`() {
        val deck = theforget.content.CardPoolManager.startingDeckIds()
        assertEquals(9, deck.size)

        val strikeCount = deck.count { it == "Strike_R" }
        val defendCount = deck.count { it == "Defend_R" }
        assertEquals(5, strikeCount)
        assertEquals(4, defendCount)
    }

    @Test
    fun `starting relics contains Burning Blood placeholder`() {
        val relics = theforget.content.RelicPoolManager.startingRelicIds()
        assertTrue(relics.isNotEmpty())
        assertEquals("Burning Blood", relics.first())
    }
}

