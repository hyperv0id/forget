package theforget.reputation

import kotlin.math.max

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
        value = max(0, newValue)
    }

    fun add(delta: Int) {
        set(value + delta)
    }

    fun reset() {
        value = DEFAULT
    }
}

