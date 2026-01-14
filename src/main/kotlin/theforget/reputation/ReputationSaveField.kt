package theforget.reputation

import basemod.abstracts.CustomSavable

/**
 * BaseMod save field handler for [ReputationState].
 *
 * Notes:
 * - BaseMod can call onLoad with null when the save field is missing (e.g. new run or older save),
 *   so we use `Int?` and fall back to default.
 */
class ReputationSaveField : CustomSavable<Int?> {
    override fun onSave(): Int = ReputationState.get()

    override fun onLoad(data: Int?) {
        ReputationState.set(data ?: 0)
    }
}

