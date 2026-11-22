package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.SymbolResult
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ServiceJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchOriginJsonTest {
    private val application = RepositoryNarrowing(RepositoryId("r1"), "application", atRoot = false)
    private val library = RepositoryNarrowing(RepositoryId("r2"), "library", atRoot = false)
    private val query = AnalyzedQuery("task", emptyList(), emptyList(), emptyList(), emptyList())

    private fun render(origin: SearchOrigin?): JsonObject {
        val json = ServiceJson.encode(SymbolResult(query, emptyList()), ScanLevel.SYNTAX, "location", search = origin)
        return Json.parseToJsonElement(json).jsonObject
    }

    @Test
    fun `an answer without an origin has none of its fields`() {
        val fields = render(null)

        assertEquals(listOf("schemaVersion", "indexLevel", "indexLocation"), fields.keys.take(3))
        assertNull(fields["narrowedTo"])
        assertNull(fields["outsideNarrowing"])
        assertNull(fields["widenedFrom"])
        assertNull(fields["incomplete"])
    }

    @Test
    fun `an incomplete answer says so after the repository it was limited to`() {
        val fields = render(SearchOrigin.limited(application, incomplete = true))

        assertEquals(listOf("indexLocation", "narrowedTo", "incomplete"), fields.keys.drop(2).take(3))
        assertEquals("true", fields.getValue("incomplete").toString())
    }

    @Test
    fun `an identity outside the narrowing names its repository`() {
        val fields = render(SearchOrigin(application, outside = library))

        assertEquals("\"library\"", fields.getValue("outsideNarrowing").toString())
        assertNull(fields["incomplete"])
    }

    @Test
    fun `a widened answer names the repository that had nothing and no limit`() {
        val fields = render(SearchOrigin(widenedFrom = application))

        assertEquals("\"application\"", fields.getValue("widenedFrom").toString())
        assertNull(fields["narrowedTo"])
    }

    @Test
    fun `an answer without a limit is never incomplete`() {
        assertEquals(SearchOrigin(), SearchOrigin.limited(null, incomplete = true))
    }
}
