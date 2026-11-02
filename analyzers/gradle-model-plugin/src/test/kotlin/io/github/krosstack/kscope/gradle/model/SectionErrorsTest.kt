package io.github.krosstack.kscope.gradle.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SectionErrorsTest {
    @Test
    fun `a failing section yields null and is recorded under its name`() {
        val errors = SectionErrors()
        assertEquals("fine", errors.guard("kotlin") { "fine" })
        assertNull(errors.guard("android", "variant") { error("unreadable") })
        assertEquals(mapOf("android" to listOf("variant: java.lang.IllegalStateException: unreadable")), errors.asMap())
    }

    @Test
    fun `a renamed plugin api is a recorded failure and not a crash`() {
        val errors = SectionErrors()
        assertNull(errors.guard("classpaths") { throw NoSuchMethodError("Gone.method()") })
        assertEquals(listOf("java.lang.NoSuchMethodError: Gone.method()"), errors.asMap().getValue("classpaths"))
    }

    @Test
    fun `sections are reported in name order with every message`() {
        val errors = SectionErrors()
        errors.guard("java") { error("one") }
        errors.guard("android") { error("two") }
        errors.guard("android") { error("three") }
        assertEquals(listOf("android", "java"), errors.asMap().keys.toList())
        assertEquals(2, errors.asMap().getValue("android").size)
    }
}
