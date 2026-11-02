package io.github.krosstack.kscope.detekt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleBehavingTypeTest {
    @Test
    fun `a file with one type with behaviour passes`() {
        val findings = findingsIn(
            """
            class Counter {
                fun increment() = Unit
                fun reset() = Unit
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a file with two types with behaviour is reported once, naming both`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = Unit
                fun second() = Unit
            }

            class Beta {
                fun first() = Unit
                fun second() = Unit
            }
            """,
        )

        val message = findings.single().message
        assertTrue("Alpha" in message && "Beta" in message, message)
    }

    @Test
    fun `objects count as types with behaviour`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = Unit
                fun second() = Unit
            }

            object Beta {
                fun first() = Unit
                fun second() = Unit
            }
            """,
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `a type with a single function is not a type with behaviour`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = Unit
                fun second() = Unit
            }

            class Beta {
                fun only() = Unit
            }

            interface Gamma {
                fun only()
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a private second type passes`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = Helper().run()
                fun second() = Unit
            }

            private class Helper {
                fun run() = Unit
                fun stop() = Unit
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a data class beside a type with behaviour passes`() {
        val findings = findingsIn(
            """
            data class Point(val x: Int, val y: Int) {
                fun norm() = x + y
            }

            class Grid {
                fun add(point: Point) = point
                fun remove(point: Point) = point
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `an interface with two functions beside a class with two functions is reported`() {
        val findings = findingsIn(
            """
            interface Reader {
                fun open()
                fun close()
            }

            class Writer {
                fun open() = Unit
                fun close() = Unit
            }
            """,
        )

        val message = findings.single().message
        assertTrue("Reader" in message && "Writer" in message, message)
    }

    @Test
    fun `functions of a companion do not count for the outer type`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun only() = Unit

                companion object {
                    fun create() = Alpha()
                    fun createAll() = listOf(Alpha())
                }
            }

            class Beta {
                fun first() = Unit
                fun second() = Unit
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a file with no type passes`() {
        val findings = findingsIn(
            """
            fun first() = Unit

            fun second() = Unit
            """,
        )

        assertTrue(findings.isEmpty())
    }
}
