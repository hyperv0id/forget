package theforget.reputation

/**
 * Gameplay-facing reputation rules derived from the raw reputation value.
 *
 * This is a pure logic layer (no state, no game objects) so that future systems
 * (Pollution clean UI, Affinity, Imitate cards) can share the same thresholds.
 */
object ReputationRules {
    /**
     * Default balancing range for reputation.
     *
     * This is NOT a hard clamp: values may exceed this range due to cards/events/debug,
     * but gameplay effects remain tier-based (i.e., same tier beyond the range).
     */
    const val DEFAULT_MIN: Int = -6
    const val DEFAULT_MAX: Int = 6

    fun tier(reputation: Int): ReputationTier =
        when {
            reputation >= 4 -> ReputationTier.HIGH
            reputation >= 1 -> ReputationTier.POSITIVE
            reputation >= -3 -> ReputationTier.NEGATIVE
            else -> ReputationTier.EXTREMELY_LOW
        }

    fun canCleanPollution(reputation: Int): Boolean =
        tier(reputation) == ReputationTier.POSITIVE || tier(reputation) == ReputationTier.HIGH

    fun isPollutionCleanAutomatic(reputation: Int): Boolean =
        tier(reputation) == ReputationTier.HIGH

    fun affinityGainMultiplier(reputation: Int): Double =
        when (tier(reputation)) {
            ReputationTier.HIGH -> 2.0
            ReputationTier.POSITIVE -> 1.0
            ReputationTier.NEGATIVE -> 0.5
            ReputationTier.EXTREMELY_LOW -> 0.0
        }

    fun isImitateHpCostEnabled(reputation: Int): Boolean =
        tier(reputation) == ReputationTier.EXTREMELY_LOW

    fun isImitateDamageBonusEnabled(reputation: Int): Boolean =
        tier(reputation) == ReputationTier.EXTREMELY_LOW
}
