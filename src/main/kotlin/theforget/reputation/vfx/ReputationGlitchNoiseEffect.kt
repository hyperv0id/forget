package theforget.reputation.vfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.helpers.ImageMaster
import com.megacrit.cardcrawl.vfx.AbstractGameEffect

/**
 * A tiny, short-lived glitch effect: draws a few additive red rectangles near the player.
 *
 * This intentionally does NOT attempt true shader-based glitching/desaturation.
 */
class ReputationGlitchNoiseEffect(
    private val centerX: Float,
    private val centerY: Float,
) : AbstractGameEffect() {
    private val rectCount: Int = MathUtils.random(3, 7)
    private val color: Color = Color(1.0f, 0.15f, 0.15f, 0.55f)

    init {
        duration = 0.12f
        startingDuration = duration
    }

    override fun update() {
        duration -= com.badlogic.gdx.Gdx.graphics.deltaTime
        if (duration < 0.0f) {
            isDone = true
        }
    }

    override fun render(sb: SpriteBatch) {
        val oldColor = sb.color.cpy()
        sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        // Fade quickly.
        val t = duration / startingDuration
        color.a = 0.55f * t
        sb.color = color

        repeat(rectCount) {
            val w = MathUtils.random(8.0f, 42.0f) * Settings.scale
            val h = MathUtils.random(4.0f, 18.0f) * Settings.scale
            val x = centerX + MathUtils.random(-90.0f, 90.0f) * Settings.scale - w / 2.0f
            val y = centerY + MathUtils.random(-40.0f, 140.0f) * Settings.scale - h / 2.0f
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h)
        }

        // Restore the default blend function.
        sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sb.color = oldColor
    }

    override fun dispose() {}
}
