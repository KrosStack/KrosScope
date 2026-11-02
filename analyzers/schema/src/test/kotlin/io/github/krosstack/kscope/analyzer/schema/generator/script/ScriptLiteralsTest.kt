package io.github.krosstack.kscope.analyzer.schema.generator.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val D = "$"

private fun pathOf(token: String): String? = ScriptLiterals.path(token)?.let { "${it.anchor} ${it.relative}" }

class ScriptLiteralsTest {
    @Test
    fun `the prefixes of the directory of the build root name it`() {
        listOf("${D}rootDir", "$D{rootDir}", "$D{rootProject.rootDir}", "$D{rootProject.projectDir}").forEach {
            assertEquals("BUILD_ROOT spec/api.yaml", pathOf("\"$it/spec/api.yaml\""), it)
            assertEquals("BUILD_ROOT ", pathOf("\"$it\""), it)
        }
    }

    @Test
    fun `the prefixes of the directory of the module name it`() {
        listOf("${D}projectDir", "$D{projectDir}", "$D{project.projectDir}").forEach {
            assertEquals("MODULE ../spec", pathOf("\"$it/../spec\""), it)
        }
    }

    @Test
    fun `a relative literal and an absolute one have no prefix`() {
        assertEquals("PLAIN src/main/proto", pathOf("\"src/main/proto\""))
        assertEquals("PLAIN ../spec/api.yaml", pathOf("'../spec/api.yaml'"))
        assertEquals("PLAIN /opt/specs/api.yaml", pathOf("\"/opt/specs/api.yaml\""))
        assertEquals("PLAIN C:/specs/api.yaml", pathOf("\"C:/specs/api.yaml\""))
    }

    @Test
    fun `the literal is kept as it is written, without its quotes`() {
        assertEquals("${D}rootDir/../spec/proto", ScriptLiterals.path("\"${D}rootDir/../spec/proto\"")?.text)
        assertEquals("proto", ScriptLiterals.path("\"${D}rootDir/../spec/proto/\"")?.name)
    }

    @Test
    fun `any other template or a variable that only starts like a prefix is not a path`() {
        listOf(
            "\"${D}buildDir/api.yaml\"",
            "\"$D{buildDir}/api.yaml\"",
            "\"${D}rootDirectory/api.yaml\"",
            "\"${D}rootDir.path/api.yaml\"",
            "\"${D}rootProject.rootDir/api.yaml\"",
            "\"$D{rootDir}x/api.yaml\"",
            "\"$D{rootDir}/${D}name/api.yaml\"",
            "\"api/$D{rootDir}\"",
            "\"$D$D\"",
            "\"\"",
        ).forEach { assertNull(ScriptLiterals.path(it), it) }
    }

    @Test
    fun `a template in the single quotes of Groovy is no template, so it is no path`() {
        assertNull(ScriptLiterals.path("'${D}rootDir/api.yaml'"))
        assertNull(ScriptLiterals.path("'''$D{rootDir}/api.yaml'''"))
    }

    @Test
    fun `a literal with an escape, or one that is not a string, is no path`() {
        assertNull(ScriptLiterals.path("\"a\\\\b\""))
        assertNull(ScriptLiterals.path("name"))
        assertNull(ScriptLiterals.path("\"unterminated"))
    }

    @Test
    fun `raw strings and strings of three apostrophes hold paths too`() {
        assertEquals("BUILD_ROOT x", pathOf("\"\"\"${D}rootDir/x\"\"\""))
        assertEquals("PLAIN y z", pathOf("'''y z'''"))
    }

    @Test
    fun `a plain value is a literal with nothing to compute`() {
        assertEquals("kotlin", ScriptLiterals.plain("\"kotlin\""))
        assertEquals("sample.api", ScriptLiterals.plain("'sample.api'"))
        assertNull(ScriptLiterals.plain("\"${D}name.api\""))
        assertNull(ScriptLiterals.plain("\"\""))
        assertNull(ScriptLiterals.plain("name"))
    }
}
