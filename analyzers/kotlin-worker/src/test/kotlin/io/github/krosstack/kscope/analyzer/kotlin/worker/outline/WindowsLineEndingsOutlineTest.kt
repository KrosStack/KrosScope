package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsLineEndingsOutlineTest {
    private val fixture = OutlineFixture()

    @AfterTest
    fun closeFixture() = fixture.close()

    @Test
    fun `an include that spans lines keeps its arguments when the lines end in carriage return and line feed`() {
        val text = listOf(
            "rootProject.name = \"sample\"",
            "",
            "include(",
            "    \":core\",",
            "    \":app\",",
            ")",
        ).joinToString("\r\n", postfix = "\r\n")

        val includes = fixture.ofText("settings.gradle.kts", text).calls.named("include")

        assertEquals(listOf(listOf(":core", ":app")), includes.map { it.literals() })
    }
}
