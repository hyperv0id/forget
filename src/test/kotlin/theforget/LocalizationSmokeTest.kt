package theforget

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalizationSmokeTest {
    @Test
    fun `UIstrings contain Self and Reputation keys`() {
        val requiredResources = listOf(
            "theforgetResources/localization/zhs/UIstrings.json",
            "theforgetResources/localization/eng/UIstrings.json",
        )

        for (path in requiredResources) {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            assertNotNull(stream, "Missing classpath resource: $path")
            val text = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

            assertTrue(text.contains("\"theforget:SelfTooltip\""), "Missing key theforget:SelfTooltip in $path")
            assertTrue(text.contains("\"theforget:SelfLabel\""), "Missing key theforget:SelfLabel in $path")
            assertTrue(text.contains("\"theforget:ReputationTooltip\""), "Missing key theforget:ReputationTooltip in $path")
            assertTrue(text.contains("\"theforget:ReputationLabel\""), "Missing key theforget:ReputationLabel in $path")
        }
    }
}
