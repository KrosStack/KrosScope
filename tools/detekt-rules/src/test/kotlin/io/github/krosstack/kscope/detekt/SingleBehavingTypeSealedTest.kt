package io.github.krosstack.kscope.detekt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleBehavingTypeSealedTest {
    @Test
    fun `a sealed hierarchy passes`() {
        val findings = findingsIn(
            """
            sealed interface Shape {
                fun area(): Double
                fun label(): String
            }

            class Circle(private val radius: Double) : Shape {
                override fun area() = radius * radius * 3
                override fun label() = "circle"
            }

            class Square(private val side: Double) : Shape {
                override fun area() = side * side
                override fun label() = "square"
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a sealed class with subclasses nested and beside it passes`() {
        val findings = findingsIn(
            """
            sealed class Outcome {
                fun describe() = "outcome"
                fun isFailure() = this is Failure

                class Failure : Outcome() {
                    fun reason() = "failed"
                    fun code() = 1
                }
            }

            class Success : Outcome() {
                fun value() = 1
                fun label() = "success"
            }
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `types that extend a type which is not sealed are counted`() {
        val findings = findingsIn(
            """
            interface Shape

            class Circle : Shape {
                fun area() = 1
                fun label() = "circle"
            }

            class Square : Shape {
                fun area() = 2
                fun label() = "square"
            }
            """,
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `a sealed hierarchy does not excuse unrelated types with behaviour`() {
        val findings = findingsIn(
            """
            sealed interface Shape {
                fun area(): Double
                fun label(): String
            }

            class Circle : Shape {
                override fun area() = 1.0
                override fun label() = "circle"
            }

            class Registry {
                fun add() = Unit
                fun remove() = Unit
            }

            class Catalogue {
                fun add() = Unit
                fun remove() = Unit
            }
            """,
        )

        val message = findings.single().message
        assertTrue("Registry" in message && "Catalogue" in message && "Circle" !in message, message)
    }
}
