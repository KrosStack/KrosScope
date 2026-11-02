package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.selection.legacy.LegacyContextSelector
import io.github.krosstack.kscope.context.selection.legacy.LegacyIncrementalChoice
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FileOrderCostTest {
    @Test
    fun `files are put in order once instead of being compared at every proposal`() {
        val fixture = AccountingFixture(FILES, DECLARATIONS)
        val estimator = TokenEstimators.create(TokenizerId("chars_4"))
        val offers = fixture.offers(ContextLevel.RECOMMENDED, estimator)
        val files = offers.map { it.variants.first().artifact }.toSet().size
        val legacyOrder = CountingOrder(LegacyIncrementalChoice.fileOrder(fixture.graph))
        val currentOrder = CountingOrder(IncrementalChoice.fileOrder(fixture.graph))

        val expected = LegacyContextSelector(fixture.merge(estimator), estimator, fixture.graph, legacyOrder)
            .select(fixture.input, offers, TokenCount(BUDGET))
        val actual = ContextSelector(fixture.merge(estimator), estimator, fixture.graph, currentOrder)
            .select(fixture.input, offers, TokenCount(BUDGET))

        println("file-order files=$files legacyCalls=${legacyOrder.calls} currentCalls=${currentOrder.calls}")
        assertEquals(expected, actual)
        assertTrue(expected.fragments.isNotEmpty())
        assertTrue(files >= MINIMUM_FILES, "files=$files")
        val bound = files * ceil(log2(files.toDouble())).toInt() + files
        assertTrue(currentOrder.calls <= bound, "current=${currentOrder.calls} bound=$bound")
        assertTrue(
            legacyOrder.calls >= RATIO * currentOrder.calls,
            "legacy=${legacyOrder.calls} current=${currentOrder.calls}",
        )
    }

    private class CountingOrder(private val delegate: Comparator<ArtifactRef>) : Comparator<ArtifactRef> {
        var calls = 0
            private set

        override fun compare(left: ArtifactRef, right: ArtifactRef): Int {
            calls++
            return delegate.compare(left, right)
        }
    }

    private companion object {
        const val FILES = 200
        const val DECLARATIONS = 5
        const val BUDGET = 16_000
        const val MINIMUM_FILES = 150
        const val RATIO = 20
    }
}
