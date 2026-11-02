package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SymbolIdBuildTest {
    private val repoModule = ModuleId("repo//:core:model")

    @Test
    fun `workspace symbol follows the documented layout`() {
        val descriptor = SymbolDescriptor().namespace(
            "com",
        ).namespace("example").type("Repo").method("load", "String,Int")
        val id = SymbolIdentity.workspace(repoModule, "commonMain", descriptor).toSymbolId()
        assertEquals("kscope gradle repo//:core:model commonMain com/example/Repo#load(String,Int).", id.value)
    }

    @Test
    fun `source set defaults to a dot when it does not apply`() {
        val id = SymbolIdentity.workspace(repoModule, null, SymbolDescriptor().namespace("lib")).toSymbolId()
        assertEquals("kscope gradle repo//:core:model . lib/", id.value)
    }

    @Test
    fun `library symbols use the coordinate or a dot`() {
        val descriptor = SymbolDescriptor().namespace("kotlin").type("String")
        val known = SymbolIdentity.library(SymbolEcosystem.MAVEN, "org.jetbrains:annotations", descriptor)
        val unknown = SymbolIdentity.library(SymbolEcosystem.JDK, null, descriptor)
        assertEquals("kscope maven org.jetbrains:annotations . kotlin/String#", known.toSymbolId().value)
        assertEquals("kscope jdk . . kotlin/String#", unknown.toSymbolId().value)
    }

    @Test
    fun `every descriptor suffix is rendered`() {
        val descriptor = SymbolDescriptor().namespace("p").type("T").term("t").method("m", "A").typeParameter("X")
            .parameter("arg").meta("companion")
        assertEquals("p/T#t.m(A).[X](arg)companion:", descriptor.render())
    }

    @Test
    fun `names outside the plain character set are backtick quoted`() {
        assertEquals("`my fun`.", SymbolDescriptor().term("my fun").render())
        assertEquals("`a``b`#", SymbolDescriptor().type("a`b").render())
        assertEquals("``.", SymbolDescriptor().term("").render())
        assertEquals("a_b+c-d$.", SymbolDescriptor().term("a_b+c-d$").render())
    }

    @Test
    fun `spaces inside any part are doubled`() {
        val module = ModuleId("repo//my app:core")
        val id = SymbolIdentity.workspace(module, "common main", SymbolDescriptor().term("my fun")).toSymbolId()
        assertEquals("kscope gradle repo//my  app:core common  main `my  fun`.", id.value)
    }

    @Test
    fun `expect and actual share a descriptor and differ by source set`() {
        val descriptor = SymbolDescriptor().namespace("p").type("Platform")
        val expect = SymbolIdentity.workspace(repoModule, "commonMain", descriptor).toSymbolId()
        val actual = SymbolIdentity.workspace(repoModule, "jvmMain", descriptor).toSymbolId()
        assertEquals(expect.value.substringAfter("commonMain "), actual.value.substringAfter("jvmMain "))
        assertNotEquals(expect, actual)
    }

    @Test
    fun `a method disambiguator may not contain parentheses`() {
        assertFailsWith<IllegalArgumentException> { SymbolDescriptor().method("m", "Fun(A)") }
    }

    @Test
    fun `identity parts must not be empty or padded with spaces`() {
        val descriptor = SymbolDescriptor().type("T")
        assertFailsWith<IllegalArgumentException> { SymbolIdentity.workspace(ModuleId(""), "main", descriptor) }
        assertFailsWith<IllegalArgumentException> { SymbolIdentity.workspace(repoModule, " main", descriptor) }
        assertFailsWith<IllegalArgumentException> { SymbolIdentity.workspace(repoModule, "main", SymbolDescriptor()) }
    }

    @Test
    fun `library symbols cannot claim the gradle ecosystem`() {
        assertFailsWith<IllegalArgumentException> {
            SymbolIdentity.library(SymbolEcosystem.GRADLE, null, SymbolDescriptor().type("T"))
        }
    }
}
