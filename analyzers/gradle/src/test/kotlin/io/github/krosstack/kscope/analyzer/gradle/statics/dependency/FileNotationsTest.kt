package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileNotationsTest {
    private fun parsed(text: String) = FileNotations.parse(text)?.map { Triple(it.path, it.fromRoot, it.tree) }

    @Test
    fun `files names each literal relative to the project or to the root`() {
        assertEquals(
            listOf(Triple("libs/a.jar", false, false), Triple("libs/b.jar", true, false)),
            parsed("""files("libs/a.jar", "${'$'}rootDir/libs/b.jar")"""),
        )
        assertEquals(listOf(Triple("x.jar", false, false)), parsed("""files("${'$'}{projectDir}/x.jar")"""))
    }

    @Test
    fun `file tree gives its directory in every form`() {
        val expected = listOf(Triple("libs", false, true))
        assertEquals(expected, parsed("""fileTree("libs")"""))
        assertEquals(expected, parsed("""fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs"))"""))
        assertEquals(expected, parsed("""fileTree(dir: 'libs', include: ['*.jar'])"""))
        assertEquals(expected, parsed("""fileTree("libs") { include("*.jar") }"""))
    }

    private fun filtersOf(text: String) = FileNotations.parse(text)?.single()?.let { it.include to it.exclude }

    @Test
    fun `file tree keeps the include and exclude patterns of every form`() {
        val selected = listOf("*.jar") to listOf("old/**")
        assertEquals(selected, filtersOf("""fileTree(dir: 'libs', include: ['*.jar'], exclude: 'old/**')"""))
        val kotlinMap = """
            fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"), "exclude" to setOf("old/**")))
        """.trimIndent()
        assertEquals(selected, filtersOf(kotlinMap))
        assertEquals(selected, filtersOf("""fileTree("libs") { include("*.jar"); exclude("old/**") }"""))
        val grouped = """
            fileTree('libs') {
                include '*.jar'
                exclude 'old/**'
            }
        """.trimIndent()
        assertEquals(selected, filtersOf(grouped))
        assertEquals(
            listOf("a/*.jar", "b/*.jar") to emptyList(),
            filtersOf("""fileTree(dir: "libs", include: "a/*.jar") { include("b/*.jar") }"""),
        )
        assertEquals(emptyList<String>() to emptyList(), filtersOf("""fileTree("libs")"""))
    }

    @Test
    fun `file tree whose patterns are not literals is left unread`() {
        assertEquals(emptyList(), parsed("""fileTree(dir: 'libs', include: patterns)"""))
        assertEquals(emptyList(), parsed("""fileTree(dir: 'libs', include: ['*.jar', extra])"""))
        assertEquals(emptyList(), parsed("""fileTree("libs") { include(patterns) }"""))
        assertEquals(emptyList(), parsed("""fileTree("libs") { include("${'$'}{name}.jar") }"""))
        assertEquals(emptyList(), parsed("""fileTree("libs") { exclude { it.name.startsWith("x") } }"""))
        assertEquals(emptyList(), parsed("""fileTree(dir: libsDir, include: ['*.jar'])"""))
    }

    @Test
    fun `other interpolations are left unread and other notations are not files`() {
        assertEquals(emptyList(), parsed("""files("${'$'}{gradle.gradleUserHomeDir}/a.jar", jarPath)"""))
        assertNull(parsed("""project(":core")"""))
    }

    @Test
    fun `anchors make paths relative to the repository`() {
        val anchors = FileAnchors("apps/client", "apps")
        assertEquals("apps/client/libs/a.jar", anchors.pathOf(DeclaredFile("libs/a.jar", false, false)))
        assertEquals("apps/vendor/b.jar", anchors.pathOf(DeclaredFile("vendor/b.jar", true, false)))
        assertEquals("../kit/build/libs", anchors.pathOf(DeclaredFile("../../kit/build/libs/", true, true)))
        assertNull(anchors.pathOf(DeclaredFile("/opt/libs/c.jar", false, false)))
        assertNull(anchors.pathOf(DeclaredFile("C:/libs/c.jar", false, false)))
    }
}
