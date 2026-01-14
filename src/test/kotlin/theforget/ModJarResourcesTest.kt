package theforget

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipFile

class ModJarResourcesTest {
    @Test
    fun `mod jar resources are namespaced under theforgetResources`() {
        val jar = File("build/libs/TheForget.jar")
        assertTrue(jar.exists(), "Mod jar not found. Run ./gradlew jar first. Expected at: ${jar.absolutePath}")

        ZipFile(jar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()

            val hasNamespacedResources = entries.any { it.startsWith("theforgetResources/") }
            assertTrue(
                hasNamespacedResources,
                "Expected at least one resources entry under `theforgetResources/` in TheForget.jar, " +
                    "but none were found. Add `src/main/resources/theforgetResources/**`.\n" +
                    "First entries:\n" + entries.take(50).joinToString("\n"),
            )

            val badPrefixes = listOf("images/", "localization/", "audio/", "shaders/")
            val offenders = entries.filter { name -> badPrefixes.any { name.startsWith(it) } }
            assertTrue(
                offenders.isEmpty(),
                "Found un-namespaced resource entries in TheForget.jar (risk of cross-mod collisions):\n" +
                    offenders.take(50).joinToString("\n"),
            )

            val requiredFiles = listOf(
                "theforgetResources/localization/zhs/UIstrings.json",
                "theforgetResources/localization/eng/UIstrings.json",
                "theforgetResources/images/ui/topPanel/reputation.png",
            )
            val missingRequiredFiles = requiredFiles.filterNot { entries.contains(it) }
            assertTrue(
                missingRequiredFiles.isEmpty(),
                "Missing required localization files in TheForget.jar:\n" +
                    missingRequiredFiles.joinToString("\n"),
            )
        }
    }
}
