package io.github.krosstack.kscope.detekt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleBehavingTypeSharedPrivateTest {
    @Test
    fun `two types that share a private top-level function pass`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = quote("a")
                fun second() = Unit
            }

            class Beta {
                fun first() = quote("b")
                fun second() = Unit
            }

            private fun quote(text: String) = text
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `two types that share a private top-level property, class or type alias pass`() {
        val sharedDeclarations = listOf(
            "private val separator = \",\"" to "separator",
            "private class Marker" to "Marker().toString()",
            "private typealias Names = List<String>" to "run { val names: Names = emptyList(); names.size }",
        )

        sharedDeclarations.forEach { (declaration, use) ->
            val findings = findingsIn(
                """
                class Alpha {
                    fun first() = $use
                    fun second() = Unit
                }

                class Beta {
                    fun first() = $use
                    fun second() = Unit
                }

                $declaration
                """,
            )

            assertTrue(findings.isEmpty(), declaration)
        }
    }

    @Test
    fun `a private declaration used by one type only does not excuse the file`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = quote("a")
                fun second() = Unit
            }

            class Beta {
                fun first() = Unit
                fun second() = Unit
            }

            private fun quote(text: String) = text
            """,
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `a shared private function does not excuse a third unrelated type`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = quote("a")
                fun second() = Unit
            }

            class Beta {
                fun first() = quote("b")
                fun second() = Unit
            }

            class Gamma {
                fun first() = Unit
                fun second() = Unit
            }

            private fun quote(text: String) = text
            """,
        )

        val message = findings.single().message
        assertTrue("Alpha" in message && "Beta" in message && "Gamma" in message, message)
    }

    @Test
    fun `types linked in a chain by two different private declarations pass`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = quote("a")
                fun second() = Unit
            }

            class Beta {
                fun first() = quote("b") + bracket("b")
                fun second() = Unit
            }

            class Gamma {
                fun first() = bracket("c")
                fun second() = Unit
            }

            private fun quote(text: String) = text

            private fun bracket(text: String) = text
            """,
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a public top-level function shared by two types does not excuse the file`() {
        val findings = findingsIn(
            """
            class Alpha {
                fun first() = quote("a")
                fun second() = Unit
            }

            class Beta {
                fun first() = quote("b")
                fun second() = Unit
            }

            fun quote(text: String) = text
            """,
        )

        assertEquals(1, findings.size)
    }
}
