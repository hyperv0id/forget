package theforget.core

import basemod.BaseMod
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.localization.UIStrings

object TheForgetLocalization {
    const val SELF_TOOLTIP_KEY: String = "theforget:SelfTooltip"
    const val SELF_LABEL_KEY: String = "theforget:SelfLabel"

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
}
