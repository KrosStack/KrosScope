package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextRole

internal object FragmentEdgeCases {
    private const val TASK = "change the rounding of the doubled values"

    fun all(): List<EstimateCase> = listOf(
        evidenceNotes(),
        adjacentRanges(),
        absorbedDeclarations(),
        generatedSummary(),
        testOutline(),
        callNeighbour(),
        brokenSignature(),
        tickedSignature(),
        bareOutline(),
    )

    private fun evidenceNotes(): EstimateCase {
        val workspace = EstimateWorkspace()
        val text = "fun probable(): Int = 1\n\n\nfun heuristic(): Int = 2\n\n\nfun exact(): Int = 3\n"
        val file = workspace.file("source/Notes", text)
        val other = workspace.file("source/Other", "fun elsewhere(): Int = 4\n")
        val probable = workspace.declareLine(file, text, 1, Certainty.PROBABLE)
        val heuristic = workspace.declareLine(file, text, 4, Certainty.HEURISTIC)
        val exact = workspace.declareLine(file, text, 7)
        val elsewhere = workspace.declareLine(other, "fun elsewhere(): Int = 4\n", 1, Certainty.HEURISTIC)
        val boundary = workspace.calls(exact, elsewhere)
        val candidates = listOf(probable, heuristic, exact).mapIndexed { index, symbol ->
            ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0 - index * 0.1)
        }
        return EstimateCase(
            "evidence notes",
            workspace.fixture(),
            ContextInput(TASK, candidates, boundaries = listOf(boundary)),
        )
    }

    private fun adjacentRanges(): EstimateCase {
        val workspace = EstimateWorkspace()
        val text = "fun a(): Int = 1\nfun b(): Int = 2\n\nfun c(): Int = 3\n\n\n\nfun far(): Int = 4\n"
        val file = workspace.file("source/Adjacent", text)
        val parts = listOf(1, 2, 4, 8).map { workspace.declareLine(file, text, it) }
        val outer = workspace.declare("outer", file, TextRange(1, 1, 2, 17))
        val candidates = (parts + outer).mapIndexed { index, symbol ->
            ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0 - index * 0.05)
        }
        return EstimateCase("adjacent ranges that merge", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun absorbedDeclarations(): EstimateCase {
        val workspace = EstimateWorkspace()
        val text = "class Box {\n    fun first() = 7\n    fun second() = 9\n}\n"
        val file = workspace.file("source/Box", text)
        val members = workspace.declareLines(file, text)
        val candidates = listOf(ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0, ContextRole.EDIT)) +
            members.map { ContextCandidate(NodeRef.OfSymbol(it.id), 0.5) }
        return EstimateCase("a file that absorbs its declarations", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun generatedSummary(): EstimateCase {
        val workspace = EstimateWorkspace()
        val schemaText = "message Entity { string name = 1; }\n"
        val schema = workspace.file("schema/Entity", schemaText, ArtifactCategory.SCHEMA)
        val entity = workspace.declare("Entity", schema, TextRange(1, 1, 1, 35))
        val truth = NodeRef.OfSymbol(entity.id)
        val generated = workspace.file(
            "generated/Entity",
            "SECRET GENERATED BODY",
            generated = GeneratedInfo("test.generator", truth),
        )
        val surface = workspace.declare(
            "GeneratedEntity",
            generated,
            TextRange(1, 1, 1, 22),
            origin = SymbolOrigin.Generated("test.generator", truth),
        )
        val candidates = listOf(
            ContextCandidate(NodeRef.OfSymbol(surface.id), 1.0),
            ContextCandidate(NodeRef.OfSymbol(entity.id), 0.5),
        )
        return EstimateCase("a generated summary", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun testOutline(): EstimateCase {
        val workspace = EstimateWorkspace()
        val serviceText = "package sample\n\nfun serve(): Int = 1\n"
        val testText = "package sample\n\nfun checksServe() = serve()\nfun checksMore() = 2\n"
        val service = workspace.file("source/Service", serviceText)
        val test = workspace.file("tests/ServiceTest", testText)
        workspace.declareLine(service, serviceText, 3)
        val checks = workspace.declareLines(test, testText)
        val candidates = listOf(
            ContextCandidate(NodeRef.OfArtifact(service.ref), 1.0, ContextRole.EDIT),
            ContextCandidate(NodeRef.OfArtifact(test.ref), 0.6, ContextRole.TEST),
        ) + checks.map { ContextCandidate(NodeRef.OfSymbol(it.id), 0.3, ContextRole.TEST) }
        val input = ContextInput(TASK, candidates, tests = listOf(NodeRef.OfArtifact(test.ref)))
        return EstimateCase("a test outline", workspace.fixture(), input)
    }

    private fun callNeighbour(): EstimateCase {
        val workspace = EstimateWorkspace()
        val text = "package sample\n\nfun serve(): Int = helper()\nfun helper(): Int = 1\n"
        val file = workspace.file("source/Neighbours", text)
        val reached = workspace.file("source/Reached", "fun reached(): Int = 5\n")
        val serve = workspace.declareLine(file, text, 3)
        val reachedSymbol = workspace.declareLine(reached, "fun reached(): Int = 5\n", 1)
        workspace.calls(serve, reachedSymbol)
        val candidates = listOf(ContextCandidate(NodeRef.OfSymbol(serve.id), 1.0, ContextRole.EDIT))
        return EstimateCase("a call neighbour", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun brokenSignature(): EstimateCase {
        val workspace = EstimateWorkspace()
        val file = workspace.file("source/Broken", "fun alpha(): Int = 1\nfun bad(): Int = 2\n")
        val valid = workspace.declare("alpha", file, TextRange(1, 1, 1, 21))
        val broken = workspace.declare("bad\uD800name", file, TextRange(2, 1, 2, 19))
        val candidates = listOf(valid, broken).map(::boundary)
        return EstimateCase("lone surrogate in a signature", workspace.fixture(), ContextInput(TASK, candidates))
    }

    private fun tickedSignature(): EstimateCase {
        val workspace = EstimateWorkspace()
        val file = workspace.file("source/Ticked", "fun ticked(): Int = 1\n")
        val ticked = workspace.declare("tick`s``and```more", file, TextRange(1, 1, 1, 22))
        val input = ContextInput(TASK, listOf(boundary(ticked)))
        return EstimateCase("backticks in a signature", workspace.fixture(), input)
    }

    private fun bareOutline(): EstimateCase {
        val workspace = EstimateWorkspace()
        val file = workspace.file("tests/Bare", "just some text\n")
        val candidates = listOf(ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0, ContextRole.TEST))
        val input = ContextInput(TASK, candidates, tests = listOf(NodeRef.OfArtifact(file.ref)))
        return EstimateCase("an outline of a file without declarations", workspace.fixture(), input)
    }

    private fun boundary(symbol: Symbol) = ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0, ContextRole.BOUNDARY)
}
