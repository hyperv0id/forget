package theforget.reputation

/**
 * Run-scoped reputation ("名望") state.
 *
 * This is intentionally gameplay-agnostic: other systems can call [add]/[set]
 * without the state caring about *why* it changes.
 */
object ReputationState {
    private const val DEFAULT: Int = 0

    private var value: Int = DEFAULT

    fun get(): Int = value

    fun set(newValue: Int) {
        value = newValue
    }

    fun tier(): ReputationTier =
        when {
            value >= 4 -> ReputationTier.HIGH
            value >= 1 -> ReputationTier.POSITIVE
            value >= -3 -> ReputationTier.NEGATIVE
            else -> ReputationTier.EXTREMELY_LOW
        }

    fun add(delta: Int) {
        set(value + delta)
    }

    fun reset() {
        value = DEFAULT
    }
}
