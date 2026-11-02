package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ContextCompilerTest {
    @Test
    fun `near budget warnings preserve a fitting signature instead of discarding all context`() {
        val text = "fun first() = 7\n"
        val file = sourceArtifact("source/Sample", text)
        val symbol = declaration("first", file, TextRange(1, 1, 1, 16), "fun first()")
        val node = NodeRef.OfSymbol(symbol.id)
        val unresolved = Relationship(
            node,
            RelationKind.CALLS,
            null,
            Resolution.Unresolved("missing", ReasonCode("test.missing")),
            EvidenceClass.COMPILER,
            null,
            Scope(),
        )
        val signature = ContextFragment(
            file.ref, null, node, ContextRendering.SIGNATURE, ContextRole.READ, 1.0,
            TokenCount(11), "fun first()", false,
        )
        val estimator = CharacterEstimator()
        val budget = estimator.count(ContextMarkdown.renderContents("fix", listOf(signature), listOf(unresolved)))
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val input = ContextInput("fix", listOf(ContextCandidate(node, 1.0)), unresolved = listOf(unresolved))

        val result = ContextCompiler(fixture, fixture, estimator, TokenCount(0)).compile(input, budget = budget)

        assertTrue(result.fragments.isNotEmpty())
        assertTrue(result.totalTokens.value <= budget.value)
        assertEquals(listOf(unresolved), result.unresolved)
    }

    @Test
    fun `unproven generated origins never fall back to declaration bodies`() {
        val text = "SECRET GENERATED BODY"
        val file = sourceArtifact("source/Sample", text)
        val symbol = declaration("generated", file, TextRange(1, 1, 1, 22), "class Generated").copy(
            origin = SymbolOrigin.Generated("test.generator", null),
        )
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val input = ContextInput("fix", listOf(ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0)))

        val result = ContextCompiler(fixture, fixture, CharacterEstimator()).compile(input, ContextLevel.FULL)

        assertTrue(result.fragments.isEmpty())
        assertTrue(result.omitted.any { it.reason == ContextOmissionReason.NO_SOURCE })
        assertEquals(0, fixture.reads.getOrDefault(file.ref, 0))
    }

    @Test
    fun `recommended test bodies remain excluded when the same test has a ranked read symbol`() {
        val text = "fun verifies() {\n    error(\"BODY_SECRET\")\n}\n"
        val file = sourceArtifact("tests/Sample", text)
        val symbol = declaration("verifies", file, TextRange(1, 1, 4, 1), "fun verifies()")
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val input = ContextInput(
            "fix",
            listOf(
                ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0, ContextRole.TEST),
                ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0),
            ),
            tests = listOf(NodeRef.OfArtifact(file.ref)),
        )
        val compiler = ContextCompiler(fixture, fixture, CharacterEstimator())

        val recommended = compiler.compile(input)
        val deep = compiler.compile(input, ContextLevel.DEEP)

        assertFalse(recommended.markdown.contains("BODY_SECRET"))
        assertTrue(recommended.fragments.none { it.sourceBacked })
        assertTrue(deep.markdown.contains("BODY_SECRET"))
    }

    @Test
    fun `declarations retain exact exclusive source bounds without unrelated bodies`() {
        val text = "package sample\n\nfun first(): Int = 7\n\nfun unrelated(): Int = 9\n"
        val file = sourceArtifact("source/Sample", text)
        val range = TextRange(3, 1, 3, 21)
        val symbol = declaration("first", file, range, "fun first(): Int")
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val input = ContextInput("fix first", listOf(ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0)))

        val result = ContextCompiler(fixture, fixture, CharacterEstimator()).compile(input, ContextLevel.MINIMAL)

        assertEquals(1, result.fragments.size)
        assertEquals("fun first(): Int = 7", result.fragments.single().text)
        assertEquals(range, result.fragments.single().range)
        assertTrue(result.fragments.single().sourceBacked)
    }

    @Test
    fun `budget includes the exact final Markdown metadata and fences`() {
        val text = "fun first() = 7\n"
        val file = sourceArtifact("source/Sample", text)
        val symbol = declaration("first", file, TextRange(1, 1, 1, 16), "fun first()")
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val input = ContextInput("fix", listOf(ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0)))
        val estimator = CharacterEstimator()
        val budget = TokenCount(120)

        val result = ContextCompiler(fixture, fixture, estimator).compile(input, budget = budget)

        assertTrue(result.fragments.isNotEmpty())
        assertEquals(estimator.count(ContextMarkdown.render(result)), result.totalTokens)
        assertTrue(result.totalTokens.value <= budget.value)
    }

    @Test
    fun `full files subsume duplicate snippets and preserve trailing newlines`() {
        val text = "fun first() = 7\nfun second() = 9\n"
        val file = sourceArtifact("source/Sample", text)
        val symbol = declaration("first", file, TextRange(1, 1, 1, 16), "fun first()")
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val candidates = listOf(
            ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0),
            ContextCandidate(NodeRef.OfSymbol(symbol.id), 0.5),
        )

        val result = ContextCompiler(fixture, fixture, CharacterEstimator())
            .compile(ContextInput("fix", candidates), ContextLevel.FULL)

        assertEquals(1, result.fragments.size)
        assertEquals(ContextRendering.FILE, result.fragments.single().rendering)
        assertEquals(TextRange(1, 1, 3, 1), result.fragments.single().range)
        assertEquals(text, result.fragments.single().text)
    }

    @Test
    fun `generated candidates emit signatures anchored to their source without reading generated bodies`() {
        val sourceText = "message Entity { string name = 1; }\n"
        val source = sourceArtifact("schema/Entity", sourceText)
        val entity = declaration("Entity", source, TextRange(1, 1, 1, 35), "message Entity")
        val generated = sourceArtifact("generated/Entity", "SECRET GENERATED BODY").copy(
            generated = GeneratedInfo("test.generator", NodeRef.OfSymbol(entity.id)),
        )
        val surface = declaration("GeneratedEntity", generated, TextRange(1, 1, 1, 22), "class GeneratedEntity").copy(
            origin = SymbolOrigin.Generated("test.generator", NodeRef.OfSymbol(entity.id)),
        )
        val fixture = ContextFixture(
            listOf(source, generated),
            listOf(entity, surface),
            mapOf(source.ref to sourceText, generated.ref to "SECRET GENERATED BODY"),
        )
        val input = ContextInput("fix", listOf(ContextCandidate(NodeRef.OfSymbol(surface.id), 1.0)))

        val result = ContextCompiler(fixture, fixture, CharacterEstimator()).compile(input, ContextLevel.FULL)

        assertTrue(result.fragments.any { it.rendering == ContextRendering.GENERATED && !it.sourceBacked })
        assertFalse(result.markdown.contains("SECRET GENERATED BODY"))
        assertEquals(0, fixture.reads.getOrDefault(generated.ref, 0))
    }

    @Test
    fun `tiny budgets report omissions rather than exceeding the token cap`() {
        val text = "fun first() = 7\n"
        val file = sourceArtifact("source/Sample", text)
        val fixture = ContextFixture(listOf(file), emptyList(), mapOf(file.ref to text))
        val input = ContextInput("a long task", listOf(ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0)))

        val result = ContextCompiler(fixture, fixture, CharacterEstimator()).compile(input, budget = TokenCount(1))

        assertTrue(result.totalTokens.value <= 1)
        assertTrue(result.omitted.any { it.reason == ContextOmissionReason.BUDGET })
    }

    @Test
    fun `adjacent source declarations merge without claiming normalized signature coverage`() {
        val text = "fun first() = 7\nfun second() = 9\nfun unrelated() = 0\n"
        val file = sourceArtifact("source/Sample", text)
        val first = declaration("first", file, TextRange(1, 1, 1, 16), "fun first()")
        val second = declaration("second", file, TextRange(2, 1, 2, 17), "fun second()")
        val fixture = ContextFixture(listOf(file), listOf(first, second), mapOf(file.ref to text))
        val input = ContextInput(
            "fix",
            listOf(first, second).map { ContextCandidate(NodeRef.OfSymbol(it.id), 1.0) },
        )

        val result = ContextCompiler(fixture, fixture, CharacterEstimator()).compile(input)

        assertEquals(1, result.fragments.size)
        assertTrue(result.fragments.single().sourceBacked)
        assertEquals("fun first() = 7\nfun second() = 9", result.fragments.single().text)
        assertEquals(TextRange(1, 1, 2, 17), result.fragments.single().range)
    }
}
