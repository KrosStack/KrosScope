package io.github.krosstack.kscope.benchmark.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OmissionDescriptionsTest {
    private val reasons = listOf(
        "notExpanded",
        "membersNotRead",
        "callsNotDrawn",
        "notInType",
        "notCallable",
        "withoutSymbol",
        "withinType",
        "beyondDepth",
        "stopped",
    )

    @Test
    fun `every reason of the diagram omissions carries a description`() {
        val properties = omissionProperties()
        val described = properties.filter { "description" in it.value.jsonObject }.keys

        assertEquals(reasons.sorted(), described.sorted())
        reasons.forEach {
            val description = properties.getValue(it).jsonObject.getValue("description").jsonPrimitive.content
            assertFalse(description.isBlank(), it)
        }
    }

    @Test
    fun `the beyond depth description names both causes`() {
        val description = omissionProperties().getValue("beyondDepth").jsonObject.getValue("description")

        assertEquals(
            "Links from a symbol the walk did not follow: the depth limit, or a caller followed upstream only.",
            description.jsonPrimitive.content,
        )
    }

    @Test
    fun `the counts of nodes and drawn states stay without a description`() {
        val properties = omissionProperties()

        assertFalse("description" in properties.getValue("nodes").jsonObject)
        assertFalse("description" in properties.getValue("linksByState").jsonObject)
    }

    @Test
    fun `the symbol id of a node says it is for symbol diagrams`() {
        val document = Json.parseToJsonElement(ApiSchemas.documents().getValue("diagram")).jsonObject
        val node = document.getValue("\$defs").jsonObject
            .getValue("io_github_krosstack_kscope_diagram_DiagramNode").jsonObject
            .getValue("properties").jsonObject.getValue("symbolId").jsonObject

        assertEquals(
            "The id of the symbol this node stands for; symbol diagrams only.",
            node.getValue("description").jsonPrimitive.content,
        )
    }

    @Test
    fun `no other schema carries a description`() {
        ApiSchemas.documents().filterKeys { it != "diagram" }.forEach { (name, document) ->
            assertFalse("\"description\"" in document, name)
        }
    }

    private fun omissionProperties(): JsonObject {
        val document = Json.parseToJsonElement(ApiSchemas.documents().getValue("diagram")).jsonObject
        val definitions = document.getValue("\$defs").jsonObject
        return definitions.getValue("io_github_krosstack_kscope_diagram_Omission").jsonObject
            .getValue("properties").jsonObject
    }
}
