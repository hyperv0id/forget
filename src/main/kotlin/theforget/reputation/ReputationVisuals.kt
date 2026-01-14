package theforget.reputation

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils

/**
 * Visual-only mapping from [ReputationTier] to UI/VFX styles.
 *
 * Important: this is NOT a gameplay system. It only expresses how tiers should feel.
 */
object ReputationVisuals {
    enum class IconStyle {
        HIGH_GLOW,
        NORMAL,
        DIM,
        ERROR_FLICKER,
    }

    enum class CombatAuraStyle {
        SYNC,
        NONE,
        FADE,
        GLITCH,
    }

    data class VisualState(
        val iconStyle: IconStyle,
        val combatAuraStyle: CombatAuraStyle,
    )

    fun visualState(tier: ReputationTier): VisualState =
        when (tier) {
            ReputationTier.HIGH -> VisualState(IconStyle.HIGH_GLOW, CombatAuraStyle.SYNC)
            ReputationTier.POSITIVE -> VisualState(IconStyle.NORMAL, CombatAuraStyle.NONE)
            ReputationTier.NEGATIVE -> VisualState(IconStyle.DIM, CombatAuraStyle.FADE)
            ReputationTier.EXTREMELY_LOW -> VisualState(IconStyle.ERROR_FLICKER, CombatAuraStyle.GLITCH)
        }

    /**
     * Color used to tint the *icon* (not the number).
     *
     * [timeSeconds] is used for pulsing/flicker effects. Callers can pass a monotonic timer.
     */
    fun iconTintColor(iconStyle: IconStyle, timeSeconds: Float): Color =
        when (iconStyle) {
            IconStyle.HIGH_GLOW -> {
                // Gold → Cyan pulse, subtle and "systematic".
                val t = 0.5f + 0.5f * MathUtils.sin(timeSeconds * 2.0f)
                val r = MathUtils.lerp(0.25f, 1.00f, t)
                val g = MathUtils.lerp(0.85f, 0.90f, t)
                val b = MathUtils.lerp(1.00f, 0.25f, t)
                Color(r, g, b, 1.0f)
            }
            IconStyle.NORMAL -> Color(1.0f, 1.0f, 1.0f, 1.0f)
            IconStyle.DIM -> Color(0.55f, 0.55f, 0.55f, 1.0f)
            IconStyle.ERROR_FLICKER -> {
                val flicker = if (((timeSeconds * 18.0f).toInt() % 3) == 0) 0.65f else 1.0f
                Color(1.0f, 0.25f, 0.25f, flicker)
            }
        }
}

