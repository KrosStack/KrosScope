package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SymbolIdParseTest {
    private val trickyNames = listOf(
        "plain",
        "my fun",
        "two  spaces",
        "back`tick",
        "``",
        "`",
        "dot.ted",
        "slash/ed",
        "hash#ed",
        "colon:ed",
        "(parenthesized)",
        "[bracketed]",
        "ünïcode",
        "",
        "a-b_c+d$",
    )

    @Test
    fun `parsing a built identifier returns the same identity`() {
        val identity = SymbolIdentity.workspace(
            ModuleId("repo//:core:model"),
            "commonMain",
            SymbolDescriptor().namespace("com").type("Repo").method("load", "String,Int"),
        )
        assertEquals(ParsedSymbolId.Valid(identity), SymbolId.parse(identity.toSymbolId().value))
    }

    @Test
    fun `every kind of segment round trips with every tricky name`() {
        trickyNames.forEach { name ->
            val segments = listOf(
                DescriptorSegment.Namespace(name),
                DescriptorSegment.Type(name),
                DescriptorSegment.Term(name),
                DescriptorSegment.Method(name, "this:A,vararg:B,Function"),
                DescriptorSegment.TypeParameter(name),
                DescriptorSegment.Parameter(name),
                DescriptorSegment.Meta(name),
            )
            segments.forEach { assertRoundTrips(SymbolDescriptor().plus(it)) }
        }
    }

    @Test
    fun `long descriptors with mixed segments round trip`() {
        val descriptor = SymbolDescriptor()
            .namespace("com").namespace("my pkg").type("Outer").type("In ner").method("do it", "")
            .parameter("a b").typeParameter("T").meta("m e")
        assertRoundTrips(descriptor)
    }

    @Test
    fun `library identities round trip`() {
        val identity = SymbolIdentity.library(SymbolEcosystem.MAVEN, "g:a", SymbolDescriptor().type("T"))
        assertEquals(ParsedSymbolId.Valid(identity), identity.toSymbolId().identity())
    }

    @Test
    fun `unit and source set with spaces round trip`() {
        val identity = SymbolIdentity.workspace(ModuleId("repo//my app:x"), "a b", SymbolDescriptor().type("T"))
        assertEquals(ParsedSymbolId.Valid(identity), identity.toSymbolId().identity())
    }

    @Test
    fun `malformed identifiers are reported as invalid with the original text`() {
        val malformed = listOf(
            "",
            "kscope",
            "kscope gradle unit main",
            "other gradle unit main T#",
            "kscope bazel unit main T#",
            "kscope gradle unit main T",
            "kscope gradle unit main T#extra",
            "kscope gradle unit main m(unterminated",
            "kscope gradle unit main m(a)",
            "kscope gradle unit main `open#",
            "kscope gradle unit main (arg",
            "kscope gradle unit main [T",
            "kscope gradle unit main #",
            "kscope gradle unit   main T#",
            "kscope gradle unit main  ",
        )
        malformed.forEach { text ->
            val parsed = assertIs<ParsedSymbolId.Invalid>(SymbolId.parse(text), text)
            assertEquals(text, parsed.text)
        }
    }

    private fun assertRoundTrips(descriptor: SymbolDescriptor) {
        val identity = SymbolIdentity.workspace(ModuleId("repo//:m"), "main", descriptor)
        val parsed = assertIs<ParsedSymbolId.Valid>(SymbolId.parse(identity.toSymbolId().value), descriptor.render())
        assertEquals(identity, parsed.identity)
        assertEquals(identity.toSymbolId(), parsed.identity.toSymbolId())
    }
}
