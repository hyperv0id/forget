package theforget.patches

import basemod.ReflectionHacks
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.Hitbox
import com.megacrit.cardcrawl.helpers.TipHelper
import com.megacrit.cardcrawl.helpers.input.InputHelper
import com.megacrit.cardcrawl.ui.panels.TopPanel
import theforget.core.TheForgetLocalization
import theforget.enums.TheForgetEnums

@SpirePatch2(clz = TopPanel::class, method = "updateTips")
object TopPanelSelfTooltipPatch {
    @JvmStatic
    @SpirePrefixPatch
    fun prefix(__instance: TopPanel): SpireReturn<Void> {
        val player = AbstractDungeon.player ?: return SpireReturn.Continue()
        if (player.chosenClass != TheForgetEnums.THE_FORGET) return SpireReturn.Continue()

        val hpHb = runCatching {
            ReflectionHacks.getPrivate<Hitbox>(__instance, TopPanel::class.java, "hpHb")
        }.getOrNull() ?: return SpireReturn.Continue()

        if (!hpHb.hovered) return SpireReturn.Continue()

        val ui = runCatching {
            CardCrawlGame.languagePack.getUIString(TheForgetLocalization.SELF_TOOLTIP_KEY)
        }.getOrNull() ?: return SpireReturn.Continue()

        val title = ui.TEXT.getOrNull(0) ?: return SpireReturn.Continue()
        val body = ui.TEXT.getOrNull(1) ?: ""

        // Match vanilla tooltip positioning logic in TopPanel.updateTips():
        // TipHelper.renderGenericTip(InputHelper.mX - TIP_OFF_X, TIP_Y, ...).
        val tipOffX = runCatching {
            ReflectionHacks.getPrivateStatic<Float>(TopPanel::class.java, "TIP_OFF_X")
        }.getOrNull() ?: 140.0f * Settings.scale
        val tipY = runCatching {
            ReflectionHacks.getPrivateStatic<Float>(TopPanel::class.java, "TIP_Y")
        }.getOrNull() ?: (Settings.HEIGHT.toFloat() - 120.0f * Settings.scale)

        TipHelper.renderGenericTip(
            InputHelper.mX.toFloat() - tipOffX,
            tipY,
            title,
            body,
        )

        // Stop vanilla HP tooltip from rendering (avoids stacking/jitter).
        return SpireReturn.Return(null)
    }
}
