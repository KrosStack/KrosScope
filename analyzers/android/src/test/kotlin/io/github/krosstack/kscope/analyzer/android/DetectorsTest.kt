package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectorsTest {
    private val detectors = AndroidCapabilities.detectors()

    private fun typeOf(path: String): String? {
        val candidate = ArtifactCandidate(RepoPath(path), 10) { ByteArray(0) }
        val types = detectors.mapNotNull { it.detect(candidate) }
        assertEquals(types.distinct(), types, "More than one detector claims $path")
        return types.singleOrNull()?.id
    }

    @Test
    fun `the files of a resource folder are classified by the folder`() {
        mapOf(
            "app/src/main/res/values/strings.xml" to "android.resource.values",
            "app/src/main/res/values-de/strings.xml" to "android.resource.values",
            "app/src/main/res/layout/activity_main.xml" to "android.resource.layout",
            "app/src/main/res/layout-sw600dp-land/activity_main.xml" to "android.resource.layout",
            "app/src/main/res/navigation/nav_main.xml" to "android.resource.navigation",
            "app/src/main/res/menu/menu_main.xml" to "android.resource.menu",
            "app/src/main/res/drawable/ic_shape.xml" to "android.resource.drawable",
            "app/src/main/res/drawable-xxhdpi/ic_logo.png" to "android.resource.drawable",
            "app/src/main/res/mipmap-hdpi/ic_launcher.png" to "android.resource.other",
            "app/src/main/res/raw/license.txt" to "android.resource.other",
            "app/src/main/res/xml/preferences.xml" to "android.resource.other",
            "app/src/main/res/font/inter.ttf" to "android.resource.other",
            "app/src/main/res/anim/fade.xml" to "android.resource.other",
            "app/src/main/res/color/selector.xml" to "android.resource.other",
        ).forEach { (path, type) -> assertEquals(type, typeOf(path), path) }
    }

    @Test
    fun `an unknown folder is a resource only in the conventional source set root`() {
        assertEquals("android.resource.other", typeOf("app/src/main/res/broken/note.txt"))
        assertEquals(null, typeOf("app/res/broken/note.txt"))
        assertEquals("android.resource.layout", typeOf("legacy/res/layout/main.xml"))
    }

    @Test
    fun `a values folder holds resources only in xml files`() {
        assertEquals("android.resource.other", typeOf("app/src/main/res/values/notes.txt"))
    }

    @Test
    fun `paths that are not below a resource folder or are hidden are not resources`() {
        listOf(
            "app/src/main/res/strings.xml",
            "app/src/main/res/values/nested/strings.xml",
            "app/src/main/res/values/.gitkeep",
            "app/src/main/resources/values/strings.xml",
            "values/strings.xml",
        ).forEach { assertEquals(null, typeOf(it), it) }
    }

    @Test
    fun `the manifest is detected by its file name`() {
        assertEquals("android.manifest", typeOf("app/src/main/AndroidManifest.xml"))
        assertEquals("android.manifest", typeOf("AndroidManifest.xml"))
        assertEquals(null, typeOf("app/src/main/Manifest.xml"))
    }

    @Test
    fun `detector ids are the artifact type ids`() {
        assertEquals(
            listOf(
                "android.manifest",
                "android.resource.drawable",
                "android.resource.layout",
                "android.resource.menu",
                "android.resource.navigation",
                "android.resource.other",
                "android.resource.values",
            ),
            detectors.map { it.id }.sorted(),
        )
    }
}
