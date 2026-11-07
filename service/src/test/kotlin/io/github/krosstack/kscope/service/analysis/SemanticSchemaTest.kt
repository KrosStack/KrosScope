package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ServiceJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticSchemaTest {
    @Test
    fun `analysis wire version is two with and without an index level`() {
        SampleWorkspace("tests").use { sample ->
            KrosScope.open(sample.root).use { scope ->
                scope.scan()
                val result = scope.analyze("Greeter", includeContextEstimates = false)
                val documents = listOf(ServiceJson.encode(result), ServiceJson.encode(result, null))

                assertEquals(
                    listOf("2", "2"),
                    documents.map {
                        Json.parseToJsonElement(it).jsonObject.getValue("schemaVersion").jsonPrimitive.content
                    },
                )
            }
        }
    }

    @Test
    fun `service analysis forwards ml off to retrieval`() {
        SampleWorkspace("tests").use { sample ->
            KrosScope.open(sample.root).use { scope ->
                scope.scan()

                assertEquals(
                    SemanticStatus.OFF,
                    scope.analyze("Greeter", includeContextEstimates = false, ml = MlMode.OFF).semantic,
                )
            }
        }
    }
}
