package io.github.krosstack.kscope.engine.build.archive.folders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MavenCoordinateTest {
    @Test
    fun `a coordinate names its JAR and its AAR`() {
        val coordinate = checkNotNull(MavenCoordinate.of("org.example.tools", "core", "1.0"))

        assertEquals("core-1.0.jar", coordinate.jarName)
        assertEquals("core-1.0.aar", coordinate.aarName)
        assertEquals(listOf("org", "example", "tools"), coordinate.groupDirectories)
    }

    @Test
    fun `a coordinate with a part that is not a plain name is none`() {
        listOf(
            Triple("", "core", "1.0"),
            Triple("org.example", "", "1.0"),
            Triple("org.example", "core", ""),
            Triple("org.example", "core", null),
            Triple("..", "core", "1.0"),
            Triple("org.example", ".", "1.0"),
            Triple("org.example", "core", "../1.0"),
            Triple("org/example", "core", "1.0"),
            Triple("org.example", "co re", "1.0"),
            Triple("org.example", "core", "\${version}"),
            Triple("org..example", "core", "1.0"),
            Triple("org.example", "core", "1.+"),
            Triple("org.example", "core", "[1.0,2.0)"),
            Triple("org.example", "core", "latest.integration"),
        ).forEach { (group, artifact, version) ->
            assertNull(MavenCoordinate.of(group, artifact, version), "$group:$artifact:$version")
        }
    }

    @Test
    fun `versions with qualifiers and underscores are plain names`() {
        listOf("1.0-SNAPSHOT", "2.1.0-rc.1", "1.0+build5", "20240101_1").forEach {
            assertEquals("core-$it.jar", MavenCoordinate.of("org.example", "core", it)?.jarName)
        }
    }
}
