package theforget.reputation.vfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.helpers.FontHelper
import com.megacrit.cardcrawl.vfx.AbstractGameEffect

class ReputationBinaryDigitEffect(
    private val digit: Char,
    x: Float,
    y: Float,
) : AbstractGameEffect() {
    private var x: Float = x
    private var y: Float = y

    private val startY: Float = y
    private val color: Color = Color(0.30f, 0.95f, 1.00f, 0.85f)

    init {
        duration = 0.80f
        startingDuration = duration
    }

    override fun update() {
        val t = 1.0f - (duration / startingDuration)
        y = startY + (t * 60.0f * Settings.scale)

        // Fade out toward the end.
        color.a = 0.85f * (1.0f - t)

        duration -= com.badlogic.gdx.Gdx.graphics.deltaTime
        if (duration < 0.0f) {
            isDone = true
        }
    }

    override fun render(sb: SpriteBatch) {
        FontHelper.renderFontCentered(
            sb,
            FontHelper.cardDescFont_N,
            digit.toString(),
            x,
            y,
            color,
        )
    }

    override fun dispose() {}
}

