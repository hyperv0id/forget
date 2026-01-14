package theforget.reputation

/**
 * Coarse reputation tiers used for gameplay gating and UI messaging.
 *
 * Tier boundaries are intentionally open-ended (no hard cap on reputation value):
 * - HIGH:            >= +4
 * - POSITIVE:        +1 .. +3
 * - NEGATIVE:         0 .. -3   (buffer zone; no extra battle-side effects)
 * - EXTREMELY_LOW:   <= -4
 *
 * For balancing, we assume reputation usually stays within [-6, +6], but values
 * outside that range are still valid and remain in the same tier.
 */
enum class ReputationTier {
    HIGH,
    POSITIVE,
    NEGATIVE,
    EXTREMELY_LOW,
}
