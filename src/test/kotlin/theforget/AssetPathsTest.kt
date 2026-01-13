package theforget

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipFile

class AssetPathsTest {
    @Test
    fun `STS jar contains required assets for The Forget character`() {
        val stsJarPath = System.getProperty("theforget.stsJar") ?: error("Missing system property: theforget.stsJar")
        val stsJar = File(stsJarPath)
        assertTrue(stsJar.exists(), "STS jar does not exist: $stsJar")

        ZipFile(stsJar).use { zip ->
            val required = listOf(
                // char select
                theforget.core.TheForgetAssets.CHAR_SELECT_BUTTON,
                theforget.core.TheForgetAssets.CHAR_SELECT_PORTRAIT,
                // animation + textures
                theforget.core.TheForgetAssets.SKELETON_ATLAS,
                theforget.core.TheForgetAssets.SKELETON_JSON,
                theforget.core.TheForgetAssets.SHOULDER_1,
                theforget.core.TheForgetAssets.SHOULDER_2,
                theforget.core.TheForgetAssets.CORPSE,
                // energy orb
                theforget.core.TheForgetAssets.ENERGY_ORB_VFX,
            ) + theforget.core.TheForgetAssets.ENERGY_ORB_TEXTURES.toList()

            val missing = required.filter { zip.getEntry(it) == null }
            assertTrue(missing.isEmpty(), "Missing STS assets in desktop-1.0.jar:\n${missing.joinToString("\n")}")
        }
    }
}
