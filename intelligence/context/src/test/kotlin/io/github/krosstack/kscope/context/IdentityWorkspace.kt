package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextRole

internal class IdentityWorkspace {
    private val sources = (0 until SOURCE_FILES).map { file -> generated("source/File${number(file)}", file) }
    private val tests = (0 until TEST_FILES).map { file -> generated("tests/Test${number(file)}", file) }
    private val schemaText = "message First { string name = 1; }\nmessage Second { int32 size = 1; }\n"
    private val schema = sourceArtifact("schema/Entities", schemaText)
        .copy(type = ArtifactType("test.schema", ArtifactCategory.SCHEMA))
    private val schemaSymbols = listOf(
        declaration("First", schema, TextRange(1, 1, 1, 35), "message First"),
        declaration("Second", schema, TextRange(2, 1, 2, 36), "message Second"),
    )
    private val generatedText = "class GeneratedFirst"
    private val generatedFile = sourceArtifact("generated/First", generatedText).copy(
        generated = GeneratedInfo("test.generator", NodeRef.OfSymbol(schemaSymbols.first().id)),
    )
    val generatedSurface = declaration(
        "GeneratedFirst",
        generatedFile,
        TextRange(1, 1, 1, generatedText.length + 1),
        "class GeneratedFirst",
    ).copy(origin = SymbolOrigin.Generated("test.generator", NodeRef.OfSymbol(schemaSymbols.first().id)))
    private val everything = sources + tests
    private val symbols = everything.flatMap { it.symbols } + schemaSymbols + generatedSurface

    val fixture = ContextFixture(
        everything.map { it.artifact } + schema + generatedFile,
        symbols,
        everything.associate { it.artifact.ref to it.text } +
            mapOf(schema.ref to schemaText, generatedFile.ref to generatedText),
        edges(),
    )

    val input = ContextInput(
        "change the rounding of the doubled values",
        candidates(),
        boundaries = edges().take(BOUNDARY_EDGES),
        tests = tests.map { NodeRef.OfArtifact(it.artifact.ref) },
        unresolved = listOf(unresolved(sources.first().symbols.first())),
    )

    private fun candidates(): List<ContextCandidate> {
        val files = sources.take(RANKED_FILES).mapIndexed { index, file ->
            ContextCandidate(NodeRef.OfArtifact(file.artifact.ref), 1.0 - index * FILE_STEP, role(index))
        }
        val members = sources.drop(RANKED_FILES).take(RANKED_MEMBER_FILES).flatMapIndexed { index, file ->
            file.symbols.take(RANKED_MEMBERS).mapIndexed { position, symbol ->
                ContextCandidate(NodeRef.OfSymbol(symbol.id), MEMBER_RELEVANCE - index * 0.05 - position * 0.01)
            }
        }
        val testFiles = tests.mapIndexed { index, file ->
            ContextCandidate(NodeRef.OfArtifact(file.artifact.ref), TEST_RELEVANCE - index * 0.1, ContextRole.TEST)
        }
        val others = (schemaSymbols + generatedSurface).mapIndexed { index, symbol ->
            ContextCandidate(NodeRef.OfSymbol(symbol.id), OTHER_RELEVANCE - index * 0.01)
        }
        return files + members + testFiles + others + ContextCandidate(NodeRef.OfArtifact(schema.ref), 0.2)
    }

    private fun number(index: Int): String = index.toString().padStart(2, '0')

    private fun role(index: Int): ContextRole = if (index == 0) ContextRole.EDIT else ContextRole.READ

    private fun edges(): List<Relationship> = sources.zipWithNext { left, right ->
        val from = NodeRef.OfSymbol(left.symbols.first().id)
        val target = NodeRef.OfSymbol(right.symbols.first().id)
        relationship(from, Resolution.Resolved(target, Certainty.EXACT))
    }

    private fun unresolved(symbol: Symbol): Relationship = relationship(
        NodeRef.OfSymbol(symbol.id),
        Resolution.Unresolved("missing", ReasonCode("test.missing")),
    )

    private fun relationship(from: NodeRef, resolution: Resolution): Relationship = Relationship(
        from = from,
        kind = RelationKind.CALLS,
        subKind = null,
        resolution = resolution,
        evidenceClass = EvidenceClass.COMPILER,
        site = null,
        scope = Scope(),
    )

    private fun generated(path: String, seed: Int): GeneratedFile {
        val functions = (0 until FUNCTIONS).map { index -> function(path, seed, index) }
        val text = functions.joinToString("\n\n") { it.first } + "\n"
        val artifact = sourceArtifact(path, text)
        val symbols = functions.mapIndexed { index, (body, name) ->
            val start = index * (LINES_PER_FUNCTION + 1) + 1
            declaration(
                name,
                artifact,
                TextRange(start, 1, start + LINES_PER_FUNCTION - 1, closingColumn(body)),
                "fun $name(value: Int): Int",
            )
        }
        return GeneratedFile(artifact, text, symbols)
    }

    private fun function(path: String, seed: Int, index: Int): Pair<String, String> {
        val name = "${path.substringAfterLast('/').lowercase()}Member$index"
        val body = buildString {
            appendLine("fun $name(value: Int): Int {")
            appendLine("    val doubled = value * ${seed + index + 2}")
            appendLine("    val shifted = doubled + ${seed * FUNCTIONS + index}")
            appendLine("    val bounded = shifted.coerceAtMost(${(seed + 1) * BOUND})")
            appendLine("    val remainder = bounded % ${index + 3}")
            appendLine("    val label = \"$name-\" + remainder.toString()")
            appendLine("    return bounded - remainder + label.length")
            append("}")
        }
        return body to name
    }

    private fun closingColumn(body: String): Int = body.lines().last().length + 1

    private class GeneratedFile(val artifact: Artifact, val text: String, val symbols: List<Symbol>)

    private companion object {
        const val SOURCE_FILES = 16
        const val TEST_FILES = 2
        const val FUNCTIONS = 10
        const val LINES_PER_FUNCTION = 8
        const val RANKED_FILES = 6
        const val RANKED_MEMBER_FILES = 5
        const val RANKED_MEMBERS = 3
        const val BOUNDARY_EDGES = 4
        const val FILE_STEP = 0.1
        const val MEMBER_RELEVANCE = 0.45
        const val TEST_RELEVANCE = 0.4
        const val OTHER_RELEVANCE = 0.3
        const val BOUND = 1000
    }
}
