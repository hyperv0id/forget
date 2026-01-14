package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.badlogic.gdx.graphics.Color
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

    @Test
    fun `combat tint override is only for negative tiers`() {
        val base = Color(1.0f, 1.0f, 1.0f, 1.0f)
        assertNull(ReputationVisuals.combatPlayerTintOverride(base, ReputationTier.HIGH, 0.0f))
        assertNull(ReputationVisuals.combatPlayerTintOverride(base, ReputationTier.POSITIVE, 0.0f))
        assertNotNull(ReputationVisuals.combatPlayerTintOverride(base, ReputationTier.NEGATIVE, 0.0f))
        assertNotNull(ReputationVisuals.combatPlayerTintOverride(base, ReputationTier.EXTREMELY_LOW, 0.0f))
    }
}
