package theforget.reputation

import basemod.TopPanelItem
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.FontHelper
import com.megacrit.cardcrawl.helpers.ImageMaster
import com.megacrit.cardcrawl.helpers.TipHelper
import theforget.core.TheForgetAssets
import theforget.core.TheForgetLocalization
import theforget.enums.TheForgetEnums

/**
 * Top-panel display for TheForget's Reputation ("名望") resource.
 *
 * Implemented via BaseMod TopPanelItem to avoid patching TopPanel rendering.
 */
class ReputationTopPanelItem : TopPanelItem(loadIcon(), ID) {
    companion object {
        const val ID: String = "theforget:ReputationTopPanelItem"

        private fun loadIcon(): Texture = ImageMaster.loadImage(TheForgetAssets.REPUTATION_ICON)

        private fun shouldRender(): Boolean {
            val player = AbstractDungeon.player ?: return false
            return player.chosenClass == TheForgetEnums.THE_FORGET
        }
    }

    override fun update() {
        if (!shouldRender()) return
        super.update()
    }

    override fun render(sb: SpriteBatch) {
        if (!shouldRender()) return
        super.render(sb)

        // Draw the numeric amount near the icon (similar to how many mods render counters).
        val amount = ReputationState.get().toString()
        FontHelper.renderFontCentered(
            sb,
            FontHelper.topPanelAmountFont,
            amount,
            this.x + this.hb_w * 0.65f,
            this.y + this.hb_h * 0.40f,
            Settings.CREAM_COLOR,
        )
    }

    override fun onClick() {
        // No-op for now (future: open a Reputation details screen).
    }

    override fun onHover() {
        super.onHover()
        if (!this.hitbox.hovered) return

        val ui = CardCrawlGame.languagePack.getUIString(TheForgetLocalization.REPUTATION_TOOLTIP_KEY)
        val title = ui.TEXT.getOrNull(0) ?: "Reputation"
        val bodyTemplate = ui.TEXT.getOrNull(1) ?: ""
        val body = TheForgetLocalization
            .normalizeLineBreaks(bodyTemplate)
            .replace("{0}", ReputationState.get().toString())

        // Align tooltip to the top panel area to reduce overlap with the cursor.
        val tipX = this.x - this.hb_w
        val tipY = Settings.HEIGHT.toFloat() - 120.0f * Settings.scale
        TipHelper.renderGenericTip(tipX, tipY, title, body)
    }

    override fun onUnhover() {
        super.onUnhover()
    }
}

