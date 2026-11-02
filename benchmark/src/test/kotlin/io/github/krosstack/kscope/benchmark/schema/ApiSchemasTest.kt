package io.github.krosstack.kscope.benchmark.schema

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiSchemasTest {
    @Test
    fun `published schemas match the current serialized service contracts`() {
        val root = Path.of(requireNotNull(System.getProperty("kscope.gold"))).parent.parent
        val directory = root.resolve("docs/schemas")

        ApiSchemas.documents().forEach { (name, document) ->
            assertEquals(document, Files.readString(directory.resolve("$name.schema.json")).replace("\r\n", "\n"), name)
        }
    }
}
