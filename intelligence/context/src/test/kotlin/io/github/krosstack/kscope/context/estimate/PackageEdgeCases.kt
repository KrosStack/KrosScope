package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.IdentityWorkspace
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput

internal object PackageEdgeCases {
    private const val TASK = "change the rounding of the doubled values"

    fun all(): List<EstimateCase> = listOf(
        staleFile(),
        unresolvedInsideAndOutside(),
        sameContentInTwoFiles(),
        declarationAsLargeAsItsFile(),
        emptyInput(),
        onlyATask(),
        nothingToRead(),
        identityWorkspace(),
    )

    private fun sameContentInTwoFiles(): EstimateCase {
        val workspace = EstimateWorkspace()
        val text = "fun same(): Int = 1\n"
        val files = listOf("source/A", "source/AMuchLongerNameForTheSameContent").map { workspace.file(it, text) }
        val candidates = files.mapIndexed { index, file ->
            ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0 - index * 0.1)
        }
        return EstimateCase("the same content in two files", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun declarationAsLargeAsItsFile(): EstimateCase {
        val workspace = EstimateWorkspace()
        val text = "fun only(): Int = 1\n"
        val file = workspace.file("source/Only", text)
        val only = workspace.declare("only", file, TextRange(1, 1, 2, 1))
        val candidates = listOf(ContextCandidate(NodeRef.OfSymbol(only.id), 1.0))
        return EstimateCase("a declaration as large as its file", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun staleFile(): EstimateCase {
        val workspace = EstimateWorkspace()
        val stale = workspace.file("source/Stale", "fun now(): Int = 2\n", indexed = "fun before(): Int = 1\n")
        val fresh = workspace.file("source/Fresh", "fun fresh(): Int = 3\n")
        val candidates = listOf(stale, fresh).mapIndexed { index, file ->
            ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0 - index * 0.1)
        }
        return EstimateCase("a stale file", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun unresolvedInsideAndOutside(): EstimateCase {
        val workspace = EstimateWorkspace()
        val readText = "fun read(): Int = 1\nfun write(): Int = 2\n"
        val farText = "fun far(): Int = 3\n"
        val read = workspace.file("source/Read", readText)
        val far = workspace.file("source/Far", farText)
        val inside = workspace.declareLines(read, readText)
        val outside = workspace.declareLines(far, farText)
        val unresolved = listOf(
            NodeRef.OfSymbol(inside[0].id),
            NodeRef.OfSymbol(inside[1].id),
            NodeRef.OfArtifact(read.ref),
            NodeRef.OfSymbol(outside[0].id),
            NodeRef.OfModule(ModuleId("sample//:module")),
        ).map(workspace::unresolvedFrom)
        val candidates = listOf(
            ContextCandidate(NodeRef.OfArtifact(read.ref), 1.0),
            ContextCandidate(NodeRef.OfArtifact(far.ref), 0.05),
        )
        val input = ContextInput(TASK, candidates, unresolved = unresolved)
        return EstimateCase("unresolved relationships inside and outside", workspace.fixture(), input)
    }

    private fun emptyInput(): EstimateCase =
        EstimateCase("an empty input", EstimateWorkspace().fixture(), ContextInput("", emptyList()))

    private fun onlyATask(): EstimateCase =
        EstimateCase("only a task", EstimateWorkspace().fixture(), ContextInput(TASK, emptyList()))

    private fun nothingToRead(): EstimateCase {
        val candidates = listOf(ContextCandidate(NodeRef.OfModule(ModuleId("sample//:module")), 1.0))
        return EstimateCase("candidates without source", EstimateWorkspace().fixture(), ContextInput(TASK, candidates))
    }

    private fun identityWorkspace(): EstimateCase {
        val workspace = IdentityWorkspace()
        return EstimateCase("the identity workspace", workspace.fixture, workspace.input)
    }
}
