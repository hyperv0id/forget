package theforget.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Texture loading helper that reads assets from the mod jar via ClassLoader resources.
 *
 * Why not always use [ImageMaster.loadImage]?
 * - In some ModTheSpire load paths, libGDX's internal file resolver may not see mod-jar resources
 *   early enough (or at all), causing `loadImage(...)` to return null.
 * - Loading via the mod's ClassLoader is reliable as long as the resource is packaged in the jar.
 */
object TheForgetTextures {
    private val logger = LogManager.getLogger(TheForgetTextures::class.java)
    private val cache: MutableMap<String, Texture> = mutableMapOf()

    private val fallbackTexture: Texture by lazy {
        // Avoid relying on ImageMaster being initialized.
        // This does assume libGDX has a graphics context; if not, we still try and prefer "not crashing".
        runCatching {
            val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888)
            pixmap.setColor(Color.WHITE)
            pixmap.fill()
            val texture = Texture(pixmap)
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
            pixmap.dispose()
            texture
        }.getOrElse { t ->
            logger.error("Failed to create fallback texture (Gdx init state: ${safeGdxState()})", t)
            // Last-resort: attempt a 1x1 texture even if the above failed for size reasons.
            val pixmap = Pixmap(max(1, 1), max(1, 1), Pixmap.Format.RGBA8888)
            pixmap.setColor(Color.WHITE)
            pixmap.fill()
            val texture = Texture(pixmap)
            pixmap.dispose()
            texture
        }
    }

    private fun safeGdxState(): String =
        runCatching { "app=${Gdx.app != null}, graphics=${Gdx.graphics != null}, files=${Gdx.files != null}" }
            .getOrDefault("Gdx not initialized")

    fun getOrLoad(path: String): Texture {
        cache[path]?.let { return it }

        val stream = TheForgetTextures::class.java.classLoader.getResourceAsStream(path)
        if (stream == null) {
            logger.error("Missing texture resource on classpath: $path")
            return fallbackTexture
        }

        val bytes = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }

        val texture = runCatching {
            val pixmap = Pixmap(bytes, 0, bytes.size)
            val t = Texture(pixmap)
            t.setFilter(TextureFilter.Linear, TextureFilter.Linear)
            pixmap.dispose()
            t
        }.getOrElse { t ->
            logger.error("Failed to load texture from classpath bytes: $path", t)
            return fallbackTexture
        }

        cache[path] = texture
        return texture
    }
}
