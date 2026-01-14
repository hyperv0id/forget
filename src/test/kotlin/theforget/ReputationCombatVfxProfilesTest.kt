package theforget

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import theforget.reputation.ReputationTier
import theforget.reputation.vfx.ReputationCombatVfxProfiles

class ReputationCombatVfxProfilesTest {
    @Test
    fun `high tier spawns sync visuals`() {
        val p = ReputationCombatVfxProfiles.forTier(ReputationTier.HIGH)
        assertTrue(p.divinityParticleIntervalSeconds > 0.0f)
        assertTrue(p.binaryDigitIntervalSeconds > 0.0f)
        assertTrue(p.auraIntervalSeconds > 0.0f)
        assertTrue(p.wrathParticleIntervalSeconds == 0.0f)
        assertTrue(p.glitchIntervalSeconds == 0.0f)
    }

    @Test
    fun `positive tier keeps combat visuals neutral`() {
        val p = ReputationCombatVfxProfiles.forTier(ReputationTier.POSITIVE)
        assertTrue(p.divinityParticleIntervalSeconds == 0.0f)
        assertTrue(p.binaryDigitIntervalSeconds == 0.0f)
        assertTrue(p.auraIntervalSeconds == 0.0f)
        assertTrue(p.smokeIntervalSeconds == 0.0f)
        assertTrue(p.wrathParticleIntervalSeconds == 0.0f)
        assertTrue(p.glitchIntervalSeconds == 0.0f)
    }

    @Test
    fun `negative tier is a muted buffer state`() {
        val p = ReputationCombatVfxProfiles.forTier(ReputationTier.NEGATIVE)
        assertTrue(p.smokeIntervalSeconds > 0.0f)
        assertTrue(p.auraIntervalSeconds > 0.0f)
        assertTrue(p.divinityParticleIntervalSeconds == 0.0f)
        assertTrue(p.wrathParticleIntervalSeconds == 0.0f)
        assertTrue(p.glitchIntervalSeconds == 0.0f)
    }

    @Test
    fun `extremely low tier is glitch heavy`() {
        val p = ReputationCombatVfxProfiles.forTier(ReputationTier.EXTREMELY_LOW)
        assertTrue(p.wrathParticleIntervalSeconds > 0.0f)
        assertTrue(p.glitchIntervalSeconds > 0.0f)
        assertTrue(p.auraIntervalSeconds > 0.0f)
        assertTrue(p.divinityParticleIntervalSeconds == 0.0f)
    }
}

