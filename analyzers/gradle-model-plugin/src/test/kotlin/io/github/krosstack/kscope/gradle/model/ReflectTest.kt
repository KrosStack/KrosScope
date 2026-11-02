package io.github.krosstack.kscope.gradle.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflectTest {
    class Extension(val name: String, private val enabled: Boolean) {
        fun getNamespace(): String = "sample.$name"

        fun isEnabled(): Boolean = enabled

        fun greet(who: String): String = "hello $who"

        fun explode(): Nothing = error("boom")
    }

    private val extension = Extension("ext", enabled = true)

    @Test
    fun `properties are read through getters is-getters and plain accessors`() {
        assertEquals("sample.ext", Reflect.get(extension, "namespace"))
        assertEquals(true, Reflect.get(extension, "enabled"))
        assertEquals("ext", Reflect.get(extension, "name"))
    }

    @Test
    fun `a missing property is null and a null target stays null`() {
        assertNull(Reflect.get(extension, "missing"))
        assertNull(Reflect.get(null, "name"))
        assertFalse(Reflect.has(extension, "missing"))
        assertTrue(Reflect.has(extension, "namespace"))
    }

    @Test
    fun `methods are called by name with their arguments`() {
        assertEquals("hello world", Reflect.call(extension, "greet", "world"))
    }

    @Test
    fun `an unknown method is reported and a thrown failure is passed on unwrapped`() {
        assertFailsWith<NoSuchMethodException> { Reflect.call(extension, "missing") }
        assertFailsWith<IllegalStateException> { Reflect.call(extension, "explode") }
    }

    @Test
    fun `values are flattened to plain lists and text`() {
        assertEquals(listOf("a", "b"), Values.names(listOf(Extension("b", true), Extension("a", true))))
        assertEquals(listOf(1, "x"), Values.plain(listOf(1, StringBuilder("x"))))
        assertEquals(listOf(1, 2, 3), Values.flatten(listOf(listOf(1), listOf(2, listOf(3)))))
    }
}
