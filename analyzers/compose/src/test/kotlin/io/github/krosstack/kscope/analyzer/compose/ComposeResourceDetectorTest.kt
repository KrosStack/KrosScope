package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeResourceDetectorTest {
    private val detectors = ComposeCapabilities.detectors()

    private fun typeOf(path: String): String? {
        val candidate = ArtifactCandidate(RepoPath(path), 10) { ByteArray(0) }
        return detectors.firstNotNullOfOrNull { it.detect(candidate) }?.id
    }

    private val root = "ui/shared/src/commonMain/composeResources"

    @Test
    fun `the detectors are named for the artifact types they claim`() {
        assertEquals(
            listOf(
                "compose.resource.drawable",
                "compose.resource.file",
                "compose.resource.font",
                "compose.resource.values",
            ),
            detectors.map { it.id }.sorted(),
        )
    }

    @Test
    fun `xml files in the values folders are value resources, also in qualified folders`() {
        assertEquals("compose.resource.values", typeOf("$root/values/strings.xml"))
        assertEquals("compose.resource.values", typeOf("$root/values-de/strings.xml"))
        assertEquals("compose.resource.values", typeOf("$root/values-fr-rFR/more.xml"))
    }

    @Test
    fun `drawables and fonts are claimed in their folders and qualified variants`() {
        assertEquals("compose.resource.drawable", typeOf("$root/drawable/ic_flag.xml"))
        assertEquals("compose.resource.drawable", typeOf("$root/drawable-dark/ic_flag.png"))
        assertEquals("compose.resource.font", typeOf("$root/font/inter.ttf"))
        assertEquals("compose.resource.font", typeOf("$root/font-ja/inter.otf"))
    }

    @Test
    fun `everything below the files folder is a file resource`() {
        assertEquals("compose.resource.file", typeOf("$root/files/key.bin"))
        assertEquals("compose.resource.file", typeOf("$root/files/nested/deeper/data.json"))
    }

    @Test
    fun `other paths are no resources`() {
        assertEquals(null, typeOf("$root/values/strings.txt"))
        assertEquals(null, typeOf("$root/raw/sound.mp3"))
        assertEquals(null, typeOf("$root/drawable/.keep"))
        assertEquals(null, typeOf("$root/drawable/nested/ic.png"))
        assertEquals(null, typeOf("$root/strings.xml"))
        assertEquals(null, typeOf("ui/shared/src/commonMain/resources/values/strings.xml"))
        assertEquals(null, typeOf("ui/shared/src/commonMain/res/values/strings.xml"))
    }

    @Test
    fun `a location tells the folder, its qualifiers and the name of the resource`() {
        val location = checkNotNull(ComposeResourceLocations.of("$root/drawable-dark/ic_flag.png"))
        assertEquals(ResourceFolder.DRAWABLE, location.kind)
        assertEquals("drawable-dark", location.folderName)
        assertEquals("dark", location.qualifiers)
        assertEquals("ic_flag", location.baseName)
        assertEquals(null, checkNotNull(ComposeResourceLocations.of("$root/values/strings.xml")).qualifiers)
    }
}
