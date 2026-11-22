package io.github.krosstack.kscope.diagram.build.symbol.groups

import io.github.krosstack.kscope.diagram.build.symbol.SymbolGraph
import io.github.krosstack.kscope.diagram.build.symbol.SymbolWorld
import io.github.krosstack.kscope.diagram.build.symbol.diagram
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class FileGroupsTest {
    private val file = "lib/src/main/kotlin/Lib.kt"

    @Test
    fun `the same relative file in two repositories is two groups captioned with the workspace path`() {
        val world = SymbolWorld()
        world.directories["alpha"] = "alpha"
        world.directories["beta"] = "tools/beta"
        val caller = world.function("caller", file = file)
        val callee = world.function("callee", file = file, repository = "beta")
        world.call(caller, callee, 3)
        val diagram = SymbolGraph(world).diagram("app.caller")
        assertEquals(listOf("alpha/$file", "tools/beta/$file"), diagram.groups.map { it.label })
        assertEquals(listOf(1, 1), diagram.groups.map { it.members.size })
    }

    @Test
    fun `one repository at the workspace root keeps the repository relative caption`() {
        val world = SymbolWorld()
        val caller = world.function("caller", file = file)
        world.call(caller, world.function("callee", file = file), 3)
        val diagram = SymbolGraph(world).diagram("app.caller")
        assertEquals(listOf(file), diagram.groups.map { it.label })
        assertEquals(listOf(2), diagram.groups.map { it.members.size })
    }
}
