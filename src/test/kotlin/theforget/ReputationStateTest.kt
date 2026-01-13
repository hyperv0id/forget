package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import theforget.reputation.ReputationState
import theforget.reputation.ReputationTier

class ReputationStateTest {
    @Test
    fun `set allows negative reputation`() {
        ReputationState.reset()
        ReputationState.set(-1)

        assertEquals(-1, ReputationState.get())
    }

    @Test
    fun `tier mapping follows the new 4-tier spec`() {
        ReputationState.reset()

        ReputationState.set(4)
        assertEquals(ReputationTier.HIGH, ReputationState.tier())

        ReputationState.set(1)
        assertEquals(ReputationTier.POSITIVE, ReputationState.tier())

        ReputationState.set(0)
        assertEquals(ReputationTier.NEGATIVE, ReputationState.tier())

        ReputationState.set(-3)
        assertEquals(ReputationTier.NEGATIVE, ReputationState.tier())

        ReputationState.set(-4)
        assertEquals(ReputationTier.EXTREMELY_LOW, ReputationState.tier())
    }
}
