package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LegacyBudgetIdentityTest {
    private val workspace = IdentityWorkspace()

    @Test
    fun `packages at budgets of eight thousand tokens and above stay byte identical to the committed compiler`() {
        val expected = resource().lines().filter { it.isNotBlank() }
        val actual = digests()

        assertEquals(expected.size, actual.size)
        assertEquals(expected, actual)
    }

    @Test
    fun `the identity workspace binds the budget at the smallest legacy size`() {
        val estimators = estimators()

        estimators.values.forEach { estimator ->
            val bounded = ContextCompiler(workspace.fixture, workspace.fixture, estimator)
                .compile(workspace.input, ContextLevel.FULL, TokenCount(LEGACY_FLOOR))
            val unbounded = ContextCompiler(workspace.fixture, workspace.fixture, estimator)
                .compile(workspace.input, ContextLevel.FULL)

            assertTrue(bounded.totalTokens.value <= LEGACY_FLOOR)
            assertTrue(unbounded.totalTokens.value > LEGACY_FLOOR)
        }
    }

    private fun digests(): List<String> = estimators().flatMap { (name, estimator) ->
        val compiler = ContextCompiler(workspace.fixture, workspace.fixture, estimator)
        ContextLevel.entries.flatMap { level ->
            BUDGETS.map { budget ->
                val result = compiler.compile(workspace.input, level, budget?.let(::TokenCount))
                "$name ${level.name} ${budget ?: "none"} ${digest(result)} ${result.totalTokens.value}"
            }
        }
    }

    private fun estimators(): Map<String, TokenEstimator> = linkedMapOf(
        "chars" to CharacterEstimator(),
        "o200k" to TokenEstimators.create(),
    )

    private fun digest(result: ContextPackage): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256")
            .digest(KscopeJson.encodeToString(ContextPackage.serializer(), result).toByteArray(Charsets.UTF_8)),
    )

    private fun resource(): String = requireNotNull(javaClass.getResource(RESOURCE)) { "Missing $RESOURCE" }.readText()

    private companion object {
        const val RESOURCE = "/legacy-budget-digests.txt"
        const val LEGACY_FLOOR = 8_000
        val BUDGETS = listOf(null, 8_000, 8_001, 12_345, 16_000, 1_000_000)
    }
}
