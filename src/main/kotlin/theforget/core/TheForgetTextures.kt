package theforget.core

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.megacrit.cardcrawl.helpers.ImageMaster
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream

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

    fun getOrLoad(path: String, fallback: Texture = ImageMaster.WHITE_SQUARE_IMG): Texture {
        cache[path]?.let { return it }

        val stream = TheForgetTextures::class.java.classLoader.getResourceAsStream(path)
        if (stream == null) {
            logger.error("Missing texture resource on classpath: $path")
            return fallback
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

        val pixmap = Pixmap(bytes, 0, bytes.size)
        val texture = Texture(pixmap)
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        pixmap.dispose()

        cache[path] = texture
        return texture
    }
}

