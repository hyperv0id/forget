package theforget.characters

import basemod.abstracts.CustomPlayer
import basemod.animations.SpineAnimation
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.megacrit.cardcrawl.actions.AbstractGameAction
import com.megacrit.cardcrawl.cards.AbstractCard
import com.megacrit.cardcrawl.cards.blue.Claw
import com.megacrit.cardcrawl.characters.AbstractPlayer
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.EnergyManager
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.helpers.FontHelper
import com.megacrit.cardcrawl.helpers.ScreenShake
import com.megacrit.cardcrawl.screens.CharSelectInfo
import com.megacrit.cardcrawl.unlock.UnlockTracker
import theforget.content.CardPoolManager
import theforget.content.RelicPoolManager
import theforget.core.TheForgetAssets
import theforget.enums.TheForgetEnums
import java.util.ArrayList

class TheForgetCharacter(name: String) : CustomPlayer(
    name,
    requireNotNull(TheForgetEnums.THE_FORGET) { "TheForgetEnums.THE_FORGET was not initialized (SpireEnum failed?)" },
    null,
    null,
    SpineAnimation(TheForgetAssets.SKELETON_ATLAS, TheForgetAssets.SKELETON_JSON, 1.0f),
) {
    init {
        dialogX = drawX + 0.0f * Settings.scale
        dialogY = drawY + 220.0f * Settings.scale

        initializeClass(
            null,
            TheForgetAssets.SHOULDER_2,
            TheForgetAssets.SHOULDER_1,
            TheForgetAssets.CORPSE,
            loadout,
            20.0f,
            -10.0f,
            220.0f,
            290.0f,
            EnergyManager(3),
        )

        state.setAnimation(0, "Idle", true).timeScale = 0.6f
        stateData.setMix("Hit", "Idle", 0.1f)
    }

    override fun getStartingDeck(): ArrayList<String> = ArrayList(CardPoolManager.startingDeckIds())

    override fun getStartingRelics(): ArrayList<String> {
        val relics = ArrayList(RelicPoolManager.startingRelicIds())
        relics.forEach { UnlockTracker.markRelicAsSeen(it) }
        return relics
    }

    override fun getLoadout(): CharSelectInfo = CharSelectInfo(
        "The Forget",
        "Forget for Get (遗忘是为了获得)。",
        75,
        75,
        0,
        99,
        5,
        this,
        startingRelics,
        startingDeck,
        false,
    )

    override fun getTitle(playerClass: AbstractPlayer.PlayerClass): String = "the Forget"

    override fun getCardColor(): AbstractCard.CardColor = AbstractCard.CardColor.RED

    override fun getCardRenderColor(): Color = Color.RED

    override fun getCardTrailColor(): Color = Color.RED

    override fun getEnergyNumFont(): BitmapFont = FontHelper.energyNumFontRed

    override fun doCharSelectScreenSelectEffect() {
        CardCrawlGame.sound.playA("ATTACK_HEAVY", MathUtils.random(-0.2f, 0.2f))
        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.HIGH, ScreenShake.ShakeDur.XLONG, true)
    }

    override fun getCustomModeCharacterButtonSoundKey(): String = "ATTACK_HEAVY"

    override fun getLocalizedCharacterName(): String = "The Forget"

    override fun newInstance(): AbstractPlayer = TheForgetCharacter(name)

    override fun getStartCardForEvent(): AbstractCard = Claw()

    override fun getAscensionMaxHPLoss(): Int = 13

    override fun getOrb(): TextureAtlas.AtlasRegion? = null

    override fun getSlashAttackColor(): Color = Color.RED

    override fun getSpireHeartSlashEffect(): Array<AbstractGameAction.AttackEffect> = emptyArray()

    override fun getSpireHeartText(): String? = null

    override fun getVampireText(): String? = null
}
