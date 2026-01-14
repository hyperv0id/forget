package theforget.reputation.vfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.rooms.AbstractRoom
import com.megacrit.cardcrawl.vfx.BorderFlashEffect
import com.megacrit.cardcrawl.vfx.combat.SmokeBlurEffect
import com.megacrit.cardcrawl.vfx.combat.VerticalAuraEffect
import com.megacrit.cardcrawl.vfx.stance.DivinityParticleEffect
import com.megacrit.cardcrawl.vfx.stance.WrathParticleEffect
import theforget.enums.TheForgetEnums
import theforget.reputation.ReputationState
import theforget.reputation.ReputationTier
import theforget.reputation.ReputationVisuals

/**
 * Combat-only VFX feedback driven by Reputation tier.
 *
 * This is intentionally visual-only (no real stance changes, no gameplay hooks).
 */
object ReputationCombatVfxController {
    private var lastTier: ReputationTier? = null

    private var divinityTimer: Float = 0.0f
    private var binaryTimer: Float = 0.0f
    private var auraTimer: Float = 0.0f
    private var smokeTimer: Float = 0.0f
    private var wrathTimer: Float = 0.0f
    private var glitchTimer: Float = 0.0f

    fun update(deltaSeconds: Float) {
        val player = AbstractDungeon.player ?: return
        if (player.chosenClass != TheForgetEnums.THE_FORGET) return

        val room = AbstractDungeon.getCurrRoom() ?: return
        if (room.phase != AbstractRoom.RoomPhase.COMBAT) return

        val tier = ReputationState.tier()
        val visuals = ReputationVisuals.visualState(tier)
        val profile = ReputationCombatVfxProfiles.forTier(tier)

        // Tier transition flash (combat only).
        if (tier != lastTier) {
            lastTier = tier
            when (tier) {
                ReputationTier.HIGH -> AbstractDungeon.topLevelEffects.add(BorderFlashEffect(Color(0.25f, 0.95f, 1.0f, 1.0f)))
                ReputationTier.POSITIVE -> Unit
                ReputationTier.NEGATIVE -> Unit
                ReputationTier.EXTREMELY_LOW -> AbstractDungeon.topLevelEffects.add(BorderFlashEffect(Color(1.0f, 0.20f, 0.20f, 1.0f), true))
            }
        }

        // Reset timers when effect disabled (so the next time it's enabled it feels immediate).
        if (visuals.combatAuraStyle == ReputationVisuals.CombatAuraStyle.NONE) {
            divinityTimer = 0.0f
            binaryTimer = 0.0f
            auraTimer = 0.0f
            smokeTimer = 0.0f
            wrathTimer = 0.0f
            glitchTimer = 0.0f
            return
        }

        val x = player.drawX
        val y = player.drawY
        val s = Settings.scale

        when (visuals.combatAuraStyle) {
            ReputationVisuals.CombatAuraStyle.SYNC -> {
                divinityTimer += deltaSeconds
                if (profile.divinityParticleIntervalSeconds > 0.0f && divinityTimer >= profile.divinityParticleIntervalSeconds) {
                    divinityTimer = 0.0f
                    AbstractDungeon.effectList.add(DivinityParticleEffect())
                }

                auraTimer += deltaSeconds
                if (profile.auraIntervalSeconds > 0.0f && auraTimer >= profile.auraIntervalSeconds) {
                    auraTimer = 0.0f
                    AbstractDungeon.effectList.add(VerticalAuraEffect(Color(0.25f, 0.95f, 1.0f, 0.9f), x, y + 40.0f * s))
                }

                binaryTimer += deltaSeconds
                if (profile.binaryDigitIntervalSeconds > 0.0f && binaryTimer >= profile.binaryDigitIntervalSeconds) {
                    binaryTimer = 0.0f
                    val digit = if (MathUtils.randomBoolean()) '1' else '0'
                    val bx = x + MathUtils.random(-70.0f, 70.0f) * s
                    val by = y + MathUtils.random(40.0f, 140.0f) * s
                    AbstractDungeon.effectList.add(ReputationBinaryDigitEffect(digit, bx, by))
                }
            }

            ReputationVisuals.CombatAuraStyle.FADE -> {
                smokeTimer += deltaSeconds
                if (profile.smokeIntervalSeconds > 0.0f && smokeTimer >= profile.smokeIntervalSeconds) {
                    smokeTimer = 0.0f
                    AbstractDungeon.effectList.add(
                        SmokeBlurEffect(
                            x + MathUtils.random(-20.0f, 20.0f) * s,
                            y + MathUtils.random(20.0f, 120.0f) * s,
                        ),
                    )
                }

                auraTimer += deltaSeconds
                if (profile.auraIntervalSeconds > 0.0f && auraTimer >= profile.auraIntervalSeconds) {
                    auraTimer = 0.0f
                    AbstractDungeon.effectList.add(VerticalAuraEffect(Color(0.65f, 0.65f, 0.65f, 0.45f), x, y + 30.0f * s))
                }
            }

            ReputationVisuals.CombatAuraStyle.GLITCH -> {
                wrathTimer += deltaSeconds
                if (profile.wrathParticleIntervalSeconds > 0.0f && wrathTimer >= profile.wrathParticleIntervalSeconds) {
                    wrathTimer = 0.0f
                    AbstractDungeon.effectList.add(WrathParticleEffect())
                }

                auraTimer += deltaSeconds
                if (profile.auraIntervalSeconds > 0.0f && auraTimer >= profile.auraIntervalSeconds) {
                    auraTimer = 0.0f
                    AbstractDungeon.effectList.add(VerticalAuraEffect(Color(1.0f, 0.15f, 0.15f, 0.85f), x, y + 35.0f * s))
                }

                glitchTimer += deltaSeconds
                if (profile.glitchIntervalSeconds > 0.0f && glitchTimer >= profile.glitchIntervalSeconds) {
                    glitchTimer = 0.0f
                    AbstractDungeon.topLevelEffects.add(ReputationGlitchNoiseEffect(x, y))
                }
            }

            ReputationVisuals.CombatAuraStyle.NONE -> Unit
        }
    }
}
