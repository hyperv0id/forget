package theforget.patches

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.TimeUtils
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.characters.AbstractPlayer
import com.megacrit.cardcrawl.rooms.AbstractRoom
import theforget.enums.TheForgetEnums
import theforget.reputation.ReputationState
import theforget.reputation.ReputationVisuals

/**
 * Combat-only, visual-only "aura/stance-like" feedback for Reputation tiers.
 *
 * Notes:
 * - We intentionally do NOT use the real Stance system (Watcher conflict risk).
 * - We patch renderPlayerImage because player Spine rendering uses `tint.color` as the skeleton color.
 */
@SpirePatch2(clz = AbstractPlayer::class, method = "renderPlayerImage")
object PlayerReputationRenderPlayerImagePatch {
    private var didOverride: Boolean = false

    private var savedTintR: Float = 1.0f
    private var savedTintG: Float = 1.0f
    private var savedTintB: Float = 1.0f
    private var savedTintA: Float = 1.0f

    private var savedAnimX: Float = 0.0f
    private var savedAnimY: Float = 0.0f

    @JvmStatic
    @SpirePrefixPatch
    fun prefix(__instance: AbstractPlayer, __sb: SpriteBatch) {
        didOverride = false

        if (__instance.chosenClass != TheForgetEnums.THE_FORGET) return

        val room = AbstractDungeon.getCurrRoom() ?: return
        if (room.phase != AbstractRoom.RoomPhase.COMBAT) return

        val tier = ReputationState.tier()
        val timeSeconds = TimeUtils.millis().toFloat() / 1000.0f
        val overrideColor = ReputationVisuals.combatPlayerTintOverride(__instance.tint.color, tier, timeSeconds) ?: return

        // Save state (restore in postfix).
        didOverride = true
        savedTintR = __instance.tint.color.r
        savedTintG = __instance.tint.color.g
        savedTintB = __instance.tint.color.b
        savedTintA = __instance.tint.color.a
        savedAnimX = __instance.animX
        savedAnimY = __instance.animY

        __instance.tint.color.r = overrideColor.r
        __instance.tint.color.g = overrideColor.g
        __instance.tint.color.b = overrideColor.b
        __instance.tint.color.a = overrideColor.a

        val jitterScale = ReputationVisuals.combatJitterAmplitudeScale(tier)
        if (jitterScale > 0.0f) {
            val jitter = 2.0f * Settings.scale * jitterScale
            __instance.animX = savedAnimX + MathUtils.random(-jitter, jitter)
            __instance.animY = savedAnimY + MathUtils.random(-jitter, jitter)
        }
    }

    @JvmStatic
    @SpirePostfixPatch
    fun postfix(__instance: AbstractPlayer, __sb: SpriteBatch) {
        if (!didOverride) return

        __instance.tint.color.r = savedTintR
        __instance.tint.color.g = savedTintG
        __instance.tint.color.b = savedTintB
        __instance.tint.color.a = savedTintA

        __instance.animX = savedAnimX
        __instance.animY = savedAnimY

        didOverride = false
    }
}

