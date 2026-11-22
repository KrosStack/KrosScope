package io.github.krosstack.kscope.cli.serve.semantic

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.FakeSemanticService
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import java.nio.file.Path

internal class SemanticQueryFixture(val directory: Path, documentCount: Int = 12, documentLines: Int = 20) :
    ScanRunner {
    val root: Path = directory.resolve("workspace")
    val fake = FakeSemanticService(directory.resolve("components"))
    val context = listOf("context", "anchor", "--budget", "500")
    val analyze = listOf("analyze", "anchor", "--as", "task")

    init {
        writeFile(root, "settings.gradle.kts", "include(\":lib\")\n")
        writeFile(root, "lib/build.gradle.kts", "")
        repeat(documentCount) {
            writeFile(
                root,
                "lib/docs/anchor-$it.md",
                "# Anchor $it\n" + "Original anchor example $it.\n".repeat(documentLines),
            )
        }
        open(root, OpenOptions(semanticIndexing = SemanticIndexingMode.FOREGROUND)).use { it.scan() }
    }

    override fun run(workspace: Path, options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult =
        open(workspace, options).use { it.scan(rebuild, verify) }

    override fun open(workspace: Path, options: OpenOptions): KrosScope = fake.open(workspace, options)

    fun local(arguments: List<String>) = KscopeCommand(execution = CommandExecution(root, scanRunner = this)).let {
        it.test(listOf("--no-server", "--workspace", root.toString(), "--quiet") + arguments) { args ->
            it.parseWithExitCodes(args)
        }
    }

    fun structural(format: String, lineSeparator: String = "\n"): String = KrosScope.open(root).use { scope ->
        val analysis = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor", ml = MlMode.OFF)
        val compiler = ContextCompiler(scope.graph(), scope.readWorkspaceFiles(), TokenEstimators.create())
        val context = compiler.compile(analysis.contextInput(), ContextLevel.RECOMMENDED, TokenCount(500))
        (if (format == "json") ServiceJson.encode(context, scope.indexLevel()) else context.markdown) + lineSeparator
    }
}
