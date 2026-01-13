package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import theforget.reputation.ReputationSaveField
import theforget.reputation.ReputationState

class ReputationSaveFieldTest {
    @Test
    fun `onSave returns current reputation`() {
        ReputationState.reset()
        ReputationState.set(42)

        val saveField = ReputationSaveField()
        assertEquals(42, saveField.onSave())
    }

    @Test
    fun `onLoad restores reputation from save`() {
        ReputationState.reset()
        ReputationState.set(1)

        val saveField = ReputationSaveField()
        saveField.onLoad(99)

        assertEquals(99, ReputationState.get())
    }

    @Test
    fun `onLoad null falls back to default`() {
        ReputationState.reset()
        ReputationState.set(7)

        val saveField = ReputationSaveField()
        saveField.onLoad(null)

        assertEquals(0, ReputationState.get())
    }
}

