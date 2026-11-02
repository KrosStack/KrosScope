package io.github.krosstack.kscope.analyzer.android.linking

import kotlin.test.Test
import kotlin.test.assertEquals

internal class JvmClassesTest {
    private val namespace = "sample.app"

    private fun read(written: String, namespace: String? = this.namespace) = JvmClasses.read(written, namespace)

    @Test
    fun `a name that starts with a dot or has none is relative to the namespace`() {
        assertEquals(WrittenClass.Named("sample.app.Main"), read(".Main"))
        assertEquals(WrittenClass.Named("sample.app.Main"), read("Main"))
        assertEquals(WrittenClass.Named("sample.app.ui.Main"), read(".ui.Main"))
    }

    @Test
    fun `a name with a dot inside is qualified already`() {
        assertEquals(WrittenClass.Named("other.pkg.Main"), read("other.pkg.Main"))
        assertEquals(WrittenClass.Named("other.pkg.Main"), read("  other.pkg.Main  "))
    }

    @Test
    fun `a nested class written with a dollar sign is named with dots`() {
        assertEquals(WrittenClass.Named("sample.app.Outer.Inner"), read(".Outer\$Inner"))
        assertEquals(WrittenClass.Named("p.Outer.Inner"), read("p.Outer\$Inner"))
    }

    @Test
    fun `qualified names of the platform and of common libraries are not names of the workspace`() {
        val names = listOf(
            "android.app.Activity",
            "androidx.fragment.app.Fragment",
            "com.google.android.material.Foo",
            "java.util.List",
        )
        names.forEach { assertEquals(WrittenClass.Framework, read(it), it) }
        assertEquals(WrittenClass.Named("com.google.other.Foo"), read("com.google.other.Foo"))
    }

    @Test
    fun `a relative name is always of the workspace, whatever the namespace starts with`() {
        assertEquals(
            WrittenClass.Named("androidx.demo.Main"),
            JvmClasses.read(".Main", "androidx.demo"),
        )
    }

    @Test
    fun `a relative name needs a namespace`() {
        assertEquals(WrittenClass.NeedsNamespace, read(".Main", namespace = null))
        assertEquals(WrittenClass.NeedsNamespace, read("Main", namespace = null))
        assertEquals(WrittenClass.Named("p.Main"), read("p.Main", namespace = null))
    }

    @Test
    fun `placeholders and text that is no name are not class names`() {
        listOf("\${applicationId}.Main", "", "  ", "a b", "@string/x", "p.", "p..Main", "p.new.Main")
            .forEach { assertEquals(WrittenClass.NotAClassName, read(it), it) }
    }
}
