package theforget.core

import basemod.BaseMod
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.localization.UIStrings

object TheForgetLocalization {
    const val SELF_TOOLTIP_KEY: String = "theforget:SelfTooltip"
    const val SELF_LABEL_KEY: String = "theforget:SelfLabel"
    const val REPUTATION_TOOLTIP_KEY: String = "theforget:ReputationTooltip"
    const val REPUTATION_LABEL_KEY: String = "theforget:ReputationLabel"
    const val REPUTATION_TIER_NAMES_KEY: String = "theforget:ReputationTierNames"
    const val REPUTATION_TIER_SUMMARIES_KEY: String = "theforget:ReputationTierSummaries"

    fun langFolder(): String = when (Settings.language) {
        Settings.GameLanguage.ZHS -> "zhs"
        Settings.GameLanguage.ENG -> "eng"
        else -> "eng"
    }

    fun loadUiStrings() {
        val lang = langFolder()
        BaseMod.loadCustomStringsFile(
            UIStrings::class.java,
            "theforgetResources/localization/${lang}/UIstrings.json",
        )
    }

    /**
     * Some STS text assets use "NL" as a newline token (commonly in card descriptions).
     * TipHelper.renderGenericTip does not automatically expand it for UIStrings, so we normalize it here.
     */
    fun normalizeLineBreaks(text: String): String {
        // Keep it intentionally simple: we only use this for our own UIStrings tooltip bodies.
        return text
            .replace("NL", "\n")
            .replace(" \n", "\n")
            .replace("\n ", "\n")
    }
}
