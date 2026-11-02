package io.github.krosstack.kscope.analyzer.gradle.statics.android

import kotlin.test.Test
import kotlin.test.assertEquals

class FlavorDirectoriesTest {
    private fun config(buildTypes: List<String> = listOf("debug", "release"), flavors: List<Flavor> = emptyList()) =
        AndroidConfig(null, null, emptySet(), buildTypes, emptyList(), flavors, true, emptyMap(), emptyList())

    private fun inferred(vararg directories: String, config: AndroidConfig = config()) =
        FlavorDirectories.inferred(config, directories.toList())

    @Test
    fun `main, test source sets and build types are not flavors`() {
        val sets = listOf("main", "test", "androidTest", "testFixtures", "testDebug", "androidTestRelease")
        assertEquals(emptyList(), FlavorDirectories.inferred(config(), sets + listOf("debug", "release")))
    }

    @Test
    fun `a directory that is none of them is a flavor`() {
        assertEquals(listOf("demo", "prod"), inferred("prod", "main", "demo", "debug"))
    }

    @Test
    fun `the directory of a variant or of a test source set names the flavor it is made of`() {
        assertEquals(listOf("demo"), inferred("demoDebug"))
        assertEquals(listOf("demo"), inferred("testDemo"))
        assertEquals(listOf("demo"), inferred("androidTestDemoRelease"))
        assertEquals(listOf("demo", "prod"), inferred("demo", "testDemo", "prodDebug", "androidTestProd"))
    }

    @Test
    fun `a name that merely starts like a test source set is a flavor`() {
        assertEquals(listOf("testing"), inferred("testing"))
    }

    @Test
    fun `build types declared by the script are not flavors and flavors it declares are not repeated`() {
        val declared = config(listOf("debug", "release", "staging"), listOf(Flavor("free", null)))
        assertEquals(listOf("paid"), inferred("staging", "free", "paid", "stagingDebug", config = declared))
    }

    @Test
    fun `names that cannot be source set names are ignored`() {
        assertEquals(emptyList(), inferred("Demo", "my-flavor", "x_y"))
    }
}
