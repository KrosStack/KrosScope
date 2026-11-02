package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.gold.GoldSymbolMatcher
import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.benchmark.workspace.WorkspaceGoldLoader
import io.github.krosstack.kscope.benchmark.workspace.WorkspaceOutputs
import io.github.krosstack.kscope.benchmark.workspace.WorkspacePreparer
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceTaskGoldResolutionTest {
    @Test
    fun `every gold symbol of every task matches a scanned declaration`() {
        val goldDirectory = Path.of(checkNotNull(System.getProperty("kscope.workspaceGold")))
        val fixture = Path.of(checkNotNull(System.getProperty("kscope.workspaceFixture")))
        val tasks = WorkspaceTaskLoader.load(goldDirectory)
        val outputs = WorkspaceOutputs.load(WorkspaceGoldLoader.outputsFile(goldDirectory))
        val scratch = Files.createTempDirectory("kscope-workspace-gold-resolution-")
        try {
            val root = Files.createDirectories(scratch.resolve("workspace"))
            WorkspacePreparer().prepare(fixture, root)
            WorkspaceOutputs.write(root, outputs)

            val unresolved = KrosScope.open(root, measuredOpenOptions(scratch.resolve("index"))).use { scope ->
                scope.scan()
                val graph = scope.graph()
                val matcher = GoldSymbolMatcher(graph.structure(), WorkspaceNames.of(graph))
                val symbols = graph.symbols(SymbolQuery(limit = Int.MAX_VALUE))
                tasks.flatMap { task ->
                    task.symbols.filter { address -> symbols.none { matcher.matches(it, address) } }
                        .map { "${task.id}: ${it.kind} ${it.fqName ?: it.name} ${it.file}:${it.line}" }
                }
            }

            assertEquals(emptyList(), unresolved)
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }
}
