package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.search.SearchHit
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalysisResultJsonTest {
    @Test
    fun `analysis without a provider serializes not installed`() {
        val result = TaskRetrieval(RetrievalFixture(), RetrievalIndex()).analyze("")
        val json = KscopeJson.encodeToJsonElement(result).jsonObject

        assertEquals(
            KscopeJson.parseToJsonElement(
                """{"state":"not-installed","provider":null,"model":null,"behindChunks":0}""",
            ),
            json.getValue("semantic"),
        )
    }

    @Test
    fun `analysis with ml off serializes off`() {
        val result = TaskRetrieval(RetrievalFixture(), RetrievalIndex()).analyze("", ml = MlMode.OFF)
        val json = KscopeJson.encodeToJsonElement(result).jsonObject

        assertEquals(
            KscopeJson.parseToJsonElement("""{"state":"off","provider":null,"model":null,"behindChunks":0}"""),
            json.getValue("semantic"),
        )
    }

    @Test
    fun `other fields retain the previous serialization`() {
        val result = TaskRetrieval(RetrievalFixture(), RetrievalIndex()).analyze("")
        val previous = """
            {"query":{"text":"","terms":[],"exactMentions":[],"expansions":[],"hints":[]},
            "files":[],"symbols":[],"modules":[],"tests":[],"boundaries":[],
            "scope":{"modules":[],"sourceSets":[],"platforms":[],"variants":[],"publicSymbols":[],"crossModule":false},
            "risks":[],"unresolved":[],"evidence":{},"confidence":"none",
            "boundaryPaths":[],"boundaryPathsTruncated":false,"contextEstimates":{}}
        """.trimIndent()

        assertEquals(
            KscopeJson.parseToJsonElement(previous).jsonObject.toMap(),
            KscopeJson.encodeToJsonElement(result).jsonObject - "semantic",
        )
    }

    @Test
    fun `ml mode leaves ranked analysis fields unchanged`() {
        val file = artifact("Root.kt")
        val index = RetrievalIndex(setOf("root"), listOf(SearchHit(node("Root.kt"), 1.0, listOf("root"), emptyMap())))
        val retrieval = TaskRetrieval(RetrievalFixture(listOf(file)), index)
        val enabled = KscopeJson.encodeToJsonElement(retrieval.analyze("root")).jsonObject - "semantic"
        val off = retrieval.analyze("root", ml = MlMode.OFF)
        val disabled = KscopeJson.encodeToJsonElement(off).jsonObject - "semantic"

        assertEquals(enabled, disabled)
    }
}
