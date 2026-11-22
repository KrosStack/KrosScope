package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.freshness.IndexFreshness
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

    @Test
    fun `the location of an index found elsewhere comes right after the level`() {
        val found = ServiceJson.encode(VersionedResult(1), ScanLevel.RESOLVED, "/work/.kscope")
        val rendered = Json.parseToJsonElement(found).jsonObject

        assertEquals(listOf("schemaVersion", "indexLevel", "indexLocation", "value"), rendered.keys.toList())
        assertEquals("/work/.kscope", rendered.getValue("indexLocation").jsonPrimitive.content)
        assertEquals(
            ServiceJson.encode(VersionedResult(1), ScanLevel.RESOLVED),
            ServiceJson.encode(VersionedResult(1), ScanLevel.RESOLVED, null),
        )
    }

    @Test
    fun `the freshness of an index that is behind follows the level and the location`() {
        val commits = FreshnessCounts(2, 1, 0)
        val trees = FreshnessCounts(1, 0, 2)
        val behind = IndexFreshness(FreshnessState.BEHIND, "2026-01-02T03:04:05Z", commits, trees, 3, false, null)
        val located = ServiceJson.encode(VersionedResult(1), ScanLevel.RESOLVED, "/work/.kscope", behind)
        val unlocated = ServiceJson.encode(VersionedResult(1), ScanLevel.RESOLVED, null, behind)
        val rendered = Json.parseToJsonElement(unlocated).jsonObject

        val leading = listOf("schemaVersion", "indexLevel", "indexLocation", "indexFreshness", "value")
        assertEquals(leading, Json.parseToJsonElement(located).jsonObject.keys.toList())
        assertEquals(leading - "indexLocation", rendered.keys.toList())
        val freshness = rendered.getValue("indexFreshness").jsonObject
        val fields = listOf("state", "scannedAt", "commits", "workingTrees", "changedFiles", "estimate", "reason")
        assertEquals(fields, freshness.keys.toList())
        assertEquals("behind", freshness.getValue("state").jsonPrimitive.content)
        val counted = freshness.getValue("commits").jsonObject
        assertEquals(listOf("behind", "notBehind", "notChecked"), counted.keys.toList())
        assertEquals(2, freshness.getValue("workingTrees").jsonObject.getValue("notChecked").jsonPrimitive.int)
        assertEquals(JsonNull, freshness.getValue("reason"))
    }

    @Test
    fun `a result that carries its own index location keeps it where it is`() {
        val rendered = Json.parseToJsonElement(ServiceJson.encode(PlacedResult())).jsonObject

        assertEquals(listOf("schemaVersion", "value", "indexLocation"), rendered.keys.toList())
        assertEquals("own", rendered.getValue("indexLocation").jsonPrimitive.content)
    }

    @Serializable
    private data class PlacedResult(val value: String = "payload", val indexLocation: String = "own")

    @Serializable
    private data class LeveledResult(val value: String = "payload", val indexLevel: String = "exact")

    @Serializable
    private data class VersionedResult(val schemaVersion: Int, val value: String = "payload")
}
