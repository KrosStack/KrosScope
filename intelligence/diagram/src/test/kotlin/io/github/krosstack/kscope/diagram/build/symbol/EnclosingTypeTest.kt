package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class EnclosingTypeTest {
    private fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(ModuleId("alpha//:app"), "main", descriptor).toSymbolId()

    private val log = SymbolDescriptor().namespace("org").namespace("example").type("Log")

    @Test
    fun `a member names the type it is declared in`() {
        val member = SymbolId("kscope gradle alpha//:app main org/example/Log#Provider#d(String,String).")
        assertEquals(
            IdType.Named(SymbolId("kscope gradle alpha//:app main org/example/Log#Provider#")),
            EnclosingType.of(member),
        )
        assertEquals(IdType.Named(idOf(log)), EnclosingType.of(idOf(log.method("d", "String"))))
        assertEquals(IdType.Named(idOf(log)), EnclosingType.of(idOf(log.term("level"))))
    }

    @Test
    fun `a type names itself and a nested type names its own nearest type`() {
        assertEquals(IdType.Named(idOf(log)), EnclosingType.of(idOf(log)))
        val nested = log.type("Provider")
        assertEquals(IdType.Named(idOf(nested)), EnclosingType.of(idOf(nested)))
        assertEquals(IdType.Named(idOf(nested)), EnclosingType.of(idOf(nested.method("d"))))
    }

    @Test
    fun `enum entries and companion members belong to their enclosing type`() {
        assertEquals(IdType.Named(idOf(log)), EnclosingType.of(idOf(log.term("DEBUG"))))
        val companion = log.type("Companion")
        assertEquals(IdType.Named(idOf(companion)), EnclosingType.of(idOf(companion.term("tag"))))
    }

    @Test
    fun `a declaration nested in a member belongs to the nearest type around it`() {
        assertEquals(IdType.Named(idOf(log)), EnclosingType.of(idOf(log.method("run").method("inner"))))
        val local = log.method("run").type("Local")
        assertEquals(IdType.Named(idOf(local)), EnclosingType.of(idOf(local.method("step"))))
    }

    @Test
    fun `a top level declaration has no enclosing type`() {
        val pack = SymbolDescriptor().namespace("org").namespace("example")
        assertEquals(IdType.Absent, EnclosingType.of(idOf(pack.method("main"))))
        assertEquals(IdType.Absent, EnclosingType.of(idOf(pack.term("VERSION"))))
        assertEquals(IdType.Absent, EnclosingType.of(idOf(SymbolDescriptor().method("main"))))
    }

    @Test
    fun `the facade of a file names a type that no declaration backs`() {
        val facade = SymbolDescriptor().namespace("org").type("UtilKt")
        assertEquals(IdType.Named(idOf(facade)), EnclosingType.of(idOf(facade.method("helper"))))
    }

    @Test
    fun `an id that does not parse says nothing`() {
        assertEquals(IdType.Unstated, EnclosingType.of(SymbolId("not an id")))
        assertEquals(IdType.Unstated, EnclosingType.of(SymbolId("")))
        assertEquals(IdType.Unstated, EnclosingType.of(SymbolId("kscope gradle alpha//:app main Log")))
    }

    @Test
    fun `the source set and the unit stay those of the member`() {
        val member = SymbolIdentity.workspace(ModuleId("alpha//:lib"), "commonMain", log.method("d")).toSymbolId()
        val expected = SymbolIdentity.workspace(ModuleId("alpha//:lib"), "commonMain", log).toSymbolId()
        assertEquals(IdType.Named(expected), EnclosingType.of(member))
    }
}
