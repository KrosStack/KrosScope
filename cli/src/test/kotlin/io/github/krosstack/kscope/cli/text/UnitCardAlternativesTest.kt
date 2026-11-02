package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Readings
import io.github.krosstack.kscope.cli.text.format.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnitCardAlternativesTest {
    private val alternatives = (1..12).map {
        UnitAlternative("other$it.Greeter", "class", "lib/src/main/kotlin/Other$it.kt:1")
    }
    private val card = UnitCard(
        subject = "greet.Greeter",
        kind = "class",
        location = "lib/src/main/kotlin/Greeter.kt:1",
        signature = null,
        module = null,
        sourceSet = null,
        declares = null,
        dependsOn = emptyList(),
        referredBy = emptyList(),
        tests = emptyList(),
        impact = "No dependants.",
        alternatives = alternatives,
        pick = "greet.Greeter",
    )

    private fun render(detail: DetailLevel): String = UnitCardText.render(
        card,
        "Greeter",
        ResultFixtures.captured(detail, setOf(Section.ALTERNATIVES)),
        Readings.name("Greeter"),
    )

    @Test
    fun `standard card alternatives stop at ten and count the remainder`() {
        val text = render(DetailLevel.STANDARD)

        assertEquals(10, text.lines().count { it.contains(".kt:1") }, text)
        alternatives.take(10).forEach { assertTrue(text.contains(it.where), text) }
        alternatives.drop(10).forEach { assertFalse(text.contains(it.where), text) }
        assertTrue(text.contains("2 more (use --detail full to list them)"), text)
        assertTrue(text.contains("Other declarations with this name (12)"), text)
    }

    @Test
    fun `full card alternatives list every declaration without a row limit hint`() {
        val text = render(DetailLevel.FULL)

        assertEquals(alternatives.size, text.lines().count { it.contains(".kt:1") }, text)
        alternatives.forEach { assertTrue(text.contains(it.where), text) }
        assertFalse(text.contains("--detail full"), text)
    }
}
