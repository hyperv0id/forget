package theforget.reputation.vfx

import theforget.reputation.ReputationTier

data class ReputationCombatVfxProfile(
    val divinityParticleIntervalSeconds: Float = 0.0f,
    val binaryDigitIntervalSeconds: Float = 0.0f,
    val auraIntervalSeconds: Float = 0.0f,
    val smokeIntervalSeconds: Float = 0.0f,
    val wrathParticleIntervalSeconds: Float = 0.0f,
    val glitchIntervalSeconds: Float = 0.0f,
)

object ReputationCombatVfxProfiles {
    fun forTier(tier: ReputationTier): ReputationCombatVfxProfile =
        when (tier) {
            ReputationTier.HIGH -> ReputationCombatVfxProfile(
                divinityParticleIntervalSeconds = 0.15f,
                binaryDigitIntervalSeconds = 0.35f,
                auraIntervalSeconds = 0.45f,
            )
            ReputationTier.POSITIVE -> ReputationCombatVfxProfile()
            ReputationTier.NEGATIVE -> ReputationCombatVfxProfile(
                smokeIntervalSeconds = 0.9f,
                auraIntervalSeconds = 1.2f,
            )
            ReputationTier.EXTREMELY_LOW -> ReputationCombatVfxProfile(
                wrathParticleIntervalSeconds = 0.12f,
                glitchIntervalSeconds = 0.08f,
                auraIntervalSeconds = 0.30f,
            )
        }
}

