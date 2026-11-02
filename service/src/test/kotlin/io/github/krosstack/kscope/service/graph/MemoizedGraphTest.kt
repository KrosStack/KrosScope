package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MemoizedGraphTest {
    private fun <T> inWorkspace(block: (Path) -> T): T {
        val root = Files.createTempDirectory("kscope-memo")
        try {
            Files.writeString(root.resolve("settings.gradle.kts"), "include(\":lib\")\n")
            val sources = Files.createDirectories(root.resolve("lib/src/main/kotlin"))
            Files.writeString(root.resolve("lib/build.gradle.kts"), "")
            Files.writeString(sources.resolve("Lib.kt"), "class Lib\n")
            return block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `one command shares the bulk reads of the symbols the relationships and the artifacts`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val everySymbol = SymbolQuery(limit = Int.MAX_VALUE)
            val everyRelationship = RelationshipQuery(limit = Int.MAX_VALUE)
            val graph = { service.graph() }

            assertSame(graph().symbols(everySymbol), graph().symbols(everySymbol))
            assertSame(graph().relationships(everyRelationship), graph().relationships(everyRelationship))
            assertSame(graph().artifacts(), graph().artifacts())
            assertSame(graph().structure(), graph().structure())
            val named = SymbolQuery(name = "Lib")
            assertNotSame(graph().symbols(named), graph().symbols(named))
        }
    }

    @Test
    fun `a scan drops what was kept so that the same reader sees the new index`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val reader = service.graph()
            val before = reader.symbols(SymbolQuery(limit = Int.MAX_VALUE)).map { it.name }

            Files.writeString(root.resolve("lib/src/main/kotlin/More.kt"), "class More\n")
            service.scan()
            val after = reader.symbols(SymbolQuery(limit = Int.MAX_VALUE)).map { it.name }

            assertNotEquals(before, after)
            assertEquals(before.size + 1, after.size)
            assertSame(reader, service.graph())
        }
    }
}
