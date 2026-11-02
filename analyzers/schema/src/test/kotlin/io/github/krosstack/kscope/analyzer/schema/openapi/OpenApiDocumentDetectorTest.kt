package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.Resources
import io.github.krosstack.kscope.analyzer.schema.SchemaCapabilities
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector
import kotlin.test.Test
import kotlin.test.assertEquals

private fun typeOf(path: String, content: String, detectors: List<ArtifactDetector>): String? {
    val bytes = content.toByteArray()
    val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
    return detectors.firstNotNullOfOrNull { it.detect(candidate) }?.id
}

private fun claimed(path: String, content: String): Boolean =
    typeOf(path, content, listOf(OpenApiDocumentDetector())) == "openapi.document"

class OpenApiDocumentDetectorTest {
    @Test
    fun `a YAML file with a top-level openapi or swagger key is claimed`() {
        assertEquals(true, claimed("api/openapi.yaml", "openapi: 3.0.3\ninfo:\n  title: A\n"))
        assertEquals(true, claimed("api/spec.yml", "swagger: \"2.0\"\ninfo:\n  title: A\n"))
        assertEquals(true, claimed("api/spec.yaml", "# header\n\"openapi\": '3.1.0'\n"))
        assertEquals(true, claimed("api/spec.yaml", "info:\n  title: A\nopenapi : 3.0.0\n"))
        assertEquals(true, claimed("api/SPEC.YAML", "openapi: 3.0.0\r\npaths: {}\r\n"))
    }

    @Test
    fun `a JSON file with a top-level openapi or swagger key is claimed, with a byte order mark too`() {
        assertEquals(true, claimed("api/spec.json", "{\n  \"openapi\": \"3.0.3\",\n  \"paths\": {}\n}"))
        assertEquals(true, claimed("api/spec.json", "\uFEFF{\"info\": {\"title\": \"A\"}, \"swagger\": \"2.0\"}"))
    }

    @Test
    fun `a key that is not at the top level does not make a document`() {
        assertEquals(false, claimed("api/other.yaml", "info:\n  openapi: 3.0.0\n"))
        assertEquals(false, claimed("api/other.yaml", "description: |\n  openapi: 3.0.0\n"))
        assertEquals(false, claimed("api/other.json", "{\"info\": {\"openapi\": \"3.0.0\"}}"))
        assertEquals(false, claimed("api/other.json", "{\"tags\": [\"openapi\"], \"other\": 1}"))
        assertEquals(false, claimed("api/other.json", "{\"description\": \"\\\"openapi\\\": 1\"}"))
    }

    @Test
    fun `files without the key and files of other extensions are not claimed`() {
        assertEquals(false, claimed("deploy/compose.yaml", "services:\n  web:\n    image: nginx\n"))
        assertEquals(false, claimed("package.json", "{\"name\": \"app\", \"version\": \"1.0.0\"}"))
        assertEquals(false, claimed("notes.txt", "openapi: 3.0.0\n"))
        assertEquals(false, claimed("api/spec.yaml", ""))
        assertEquals(false, claimed("api/spec.json", "{\"openapi"))
    }

    @Test
    fun `an exported Room schema keeps its type, whichever detector comes first`() {
        val room = Resources.text("room/1.json")
        val detectors = SchemaCapabilities.detectors()

        assertEquals(false, claimed("schemas/Db/1.json", room))
        assertEquals("room.schema", typeOf("schemas/Db/1.json", room, detectors))
        assertEquals("room.schema", typeOf("schemas/Db/1.json", room, detectors.reversed()))
    }

    @Test
    fun `an OpenAPI document and a proto file are told apart by the registry of the analyzer`() {
        val detectors = SchemaCapabilities.detectors()

        assertEquals("openapi.document", typeOf("api/spec.yaml", "openapi: 3.0.0\n", detectors))
        assertEquals("openapi.document", typeOf("api/spec.json", "{\"openapi\": \"3.0.0\"}", detectors))
        assertEquals("proto.source", typeOf("api/sync.proto", "syntax = \"proto3\";", detectors))
        assertEquals(null, typeOf("api/other.yaml", "name: x\n", detectors))
    }
}
