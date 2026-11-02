package io.github.krosstack.kscope.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceJsonTest {
    @Test
    fun `the service wire version cannot be overridden by a result field`() {
        val result = Json.parseToJsonElement(ServiceJson.encode(VersionedResult(schemaVersion = 2))).jsonObject

        assertEquals(1, result.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("payload", result.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun `the level of the index comes right after the version`() {
        val resolved = Json.parseToJsonElement(ServiceJson.encode(VersionedResult(1), ScanLevel.RESOLVED)).jsonObject
        val absent = Json.parseToJsonElement(ServiceJson.encode(VersionedResult(1), null)).jsonObject

        assertEquals(listOf("schemaVersion", "indexLevel", "value"), resolved.keys.toList())
        assertEquals("resolved", resolved.getValue("indexLevel").jsonPrimitive.content)
        assertEquals(JsonNull, absent.getValue("indexLevel"))
    }

    @Test
    fun `a result that carries its own level is rendered with it after the version`() {
        val rendered = Json.parseToJsonElement(ServiceJson.encode(LeveledResult())).jsonObject

        assertEquals(listOf("schemaVersion", "indexLevel", "value"), rendered.keys.toList())
        assertEquals("exact", rendered.getValue("indexLevel").jsonPrimitive.content)
    }

    @Serializable
    private data class LeveledResult(val value: String = "payload", val indexLevel: String = "exact")

    @Serializable
    private data class VersionedResult(val schemaVersion: Int, val value: String = "payload")
}
