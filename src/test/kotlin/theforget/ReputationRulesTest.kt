package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import theforget.reputation.ReputationRules

class ReputationRulesTest {
    @Test
    fun `pollution clean gating follows tier spec`() {
        assertTrue(ReputationRules.canCleanPollution(1))
        assertTrue(ReputationRules.canCleanPollution(3))
        assertTrue(ReputationRules.canCleanPollution(4))

        assertFalse(ReputationRules.canCleanPollution(0))
        assertFalse(ReputationRules.canCleanPollution(-1))
        assertFalse(ReputationRules.canCleanPollution(-3))
        assertFalse(ReputationRules.canCleanPollution(-4))
    }

    @Test
    fun `high tier enables automatic free cleaning`() {
        assertTrue(ReputationRules.isPollutionCleanAutomatic(4))
        assertTrue(ReputationRules.isPollutionCleanAutomatic(999))

        assertFalse(ReputationRules.isPollutionCleanAutomatic(3))
        assertFalse(ReputationRules.isPollutionCleanAutomatic(0))
    }

    @Test
    fun `affinity gain multiplier follows tier spec`() {
        assertEquals(2.0, ReputationRules.affinityGainMultiplier(4))
        assertEquals(2.0, ReputationRules.affinityGainMultiplier(999))

        assertEquals(1.0, ReputationRules.affinityGainMultiplier(1))
        assertEquals(1.0, ReputationRules.affinityGainMultiplier(3))

        assertEquals(0.5, ReputationRules.affinityGainMultiplier(0))
        assertEquals(0.5, ReputationRules.affinityGainMultiplier(-3))

        assertEquals(0.0, ReputationRules.affinityGainMultiplier(-4))
        assertEquals(0.0, ReputationRules.affinityGainMultiplier(-999))
    }

    @Test
    fun `extremely low tier enables imitate penalties and damage bonus`() {
        assertTrue(ReputationRules.isImitateHpCostEnabled(-4))
        assertTrue(ReputationRules.isImitateDamageBonusEnabled(-4))

        assertFalse(ReputationRules.isImitateHpCostEnabled(-3))
        assertFalse(ReputationRules.isImitateDamageBonusEnabled(-3))
    }
}

