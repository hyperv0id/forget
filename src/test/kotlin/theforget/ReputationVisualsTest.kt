package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import theforget.reputation.ReputationTier
import theforget.reputation.ReputationVisuals

class ReputationVisualsTest {
    @Test
    fun `visual state mapping follows tier semantics`() {
        assertEquals(
            ReputationVisuals.VisualState(
                iconStyle = ReputationVisuals.IconStyle.HIGH_GLOW,
                combatAuraStyle = ReputationVisuals.CombatAuraStyle.SYNC,
            ),
            ReputationVisuals.visualState(ReputationTier.HIGH),
        )

        assertEquals(
            ReputationVisuals.VisualState(
                iconStyle = ReputationVisuals.IconStyle.NORMAL,
                combatAuraStyle = ReputationVisuals.CombatAuraStyle.NONE,
            ),
            ReputationVisuals.visualState(ReputationTier.POSITIVE),
        )

        assertEquals(
            ReputationVisuals.VisualState(
                iconStyle = ReputationVisuals.IconStyle.DIM,
                combatAuraStyle = ReputationVisuals.CombatAuraStyle.FADE,
            ),
            ReputationVisuals.visualState(ReputationTier.NEGATIVE),
        )

        assertEquals(
            ReputationVisuals.VisualState(
                iconStyle = ReputationVisuals.IconStyle.ERROR_FLICKER,
                combatAuraStyle = ReputationVisuals.CombatAuraStyle.GLITCH,
            ),
            ReputationVisuals.visualState(ReputationTier.EXTREMELY_LOW),
        )
    }
}

