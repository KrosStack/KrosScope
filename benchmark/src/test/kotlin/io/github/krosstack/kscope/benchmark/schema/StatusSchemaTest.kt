package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.freshness.IndexFreshness
import io.github.krosstack.kscope.service.status.ServerState
import io.github.krosstack.kscope.service.status.ServerStatus
import io.github.krosstack.kscope.service.status.StatusResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusSchemaTest {
    private val schema = SchemaCheck(published())
    private val freshness = IndexFreshness(
        FreshnessState.BEHIND,
        "2026-11-20T08:00:00Z",
        FreshnessCounts(2, 6, 0),
        FreshnessCounts(1, 7, 0),
        3,
        false,
        null,
    )
    private val running = ServerStatus(ServerState.RUNNING, 4120, "1.0.0", "2026-11-20T07:00:00Z", stale = false)
    private val none = ServerStatus(ServerState.NOT_RUNNING, null, null, null, stale = false)

    private fun published(): JsonObject {
        val root = Path.of(requireNotNull(System.getProperty("kscope.gold"))).parent.parent
        return Json.parseToJsonElement(Files.readString(root.resolve("docs/schemas/status.schema.json"))).jsonObject
    }

    private fun json(result: StatusResult): JsonObject = Json.parseToJsonElement(ServiceJson.encode(result)).jsonObject

    private val present = StatusResult(
        "/work/app", "/work/app/.kscope", true, "resolved", "2026-11-20T08:00:00Z", 172_800, freshness, 8,
        running, 120, "1.0.0",
    )
    private val absent = StatusResult(
        "/work/app", "/work/app/.kscope", false, null, null, null, null, 0, none, 0, "1.0.0",
    )

    @Test
    fun `status of an index conforms, with the server in each of its states`() {
        assertEquals(emptyList(), schema.violations(json(present)))
        ServerState.entries.forEach { state ->
            val server = running.copy(state = state, stale = state == ServerState.NOT_RUNNING)
            assertEquals(emptyList(), schema.violations(json(present.copy(server = server))), state.name)
        }
    }

    @Test
    fun `status without an index conforms`() {
        assertEquals(emptyList(), schema.violations(json(absent)))
    }

    @Test
    fun `the check fails for a field that the schema does not know, a missing field or a state it does not name`() {
        val valid = json(present)

        assertTrue(schema.violations(JsonObject(valid + ("surprise" to JsonPrimitive("x")))).isNotEmpty())
        assertTrue(schema.violations(JsonObject(valid - "server")).isNotEmpty())
        val server = valid.getValue("server").jsonObject
        val other = JsonObject(server + ("state" to JsonPrimitive("RUNNING")))
        assertTrue(schema.violations(JsonObject(valid + ("server" to other))).isNotEmpty())
    }
}
