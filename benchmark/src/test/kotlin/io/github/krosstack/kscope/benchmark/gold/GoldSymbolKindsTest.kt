package io.github.krosstack.kscope.benchmark.gold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoldSymbolKindsTest {
    @Test
    fun `declared kind normalization preserves the existing non-code lookup contract`() {
        val kinds = mapOf(
            "android.resource.string" to "android.resource.string",
            "aidl.method" to "aidl.method",
            "proto.message" to "proto.message",
            "compose.resource.string" to "compose.resource.string",
            "sq.table" to "sqldelight.table",
            "sq.query" to "sqldelight.query",
            "manifest.receiver" to "android.manifest.receiver",
            "android.layout" to "android.resource.layout",
            "android.navigation" to "android.resource.navigation",
        )

        kinds.forEach { (gold, actual) -> assertEquals(actual, GoldSymbolKinds.declaredKindOf(gold)) }
        assertNull(GoldSymbolKinds.declaredKindOf("unsupported.kind"))
        assertNull(GoldSymbolKinds.declaredKindOf("sqldelight.table"))
    }

    @Test
    fun `catalog normalization and literal kinds retain exact symbol-kind identity`() {
        assertEquals("gradle.catalog.library", GoldSymbolKinds.catalogKindOf("catalog.library"))
        assertTrue(GoldSymbolKinds.matches("gradle.catalog.library", "catalog.library"))
        assertTrue(GoldSymbolKinds.matches("sqldelight.table", "sqldelight.table"))
        assertTrue(GoldSymbolKinds.matches("kotlin.function", "kotlin.function"))
        assertTrue(GoldSymbolKinds.matches("kotlin.function", null))
        assertFalse(GoldSymbolKinds.matches("gradle.catalog.version", "catalog.library"))
        assertFalse(GoldSymbolKinds.matches("sqldelight.table", "sq.query"))
        assertFalse(GoldSymbolKinds.matches("sqldelight.table", "unsupported.table"))
    }
}
