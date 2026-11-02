package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

internal class SelectorPerformanceTest {
    @Test
    fun `guarded block counts preserve large generated file overlap selections at sixteen thousand tokens`() {
        val fixture = SelectorPerformanceFixture(20, 8, generated = true)
        val offers = fixture.offers()
        val frozen = measureTimedValue {
            FrozenSelectorV1(fixture.merge(), fixture.estimator, fixture.graph)
                .select(fixture.input, offers, TokenCount(16000))
        }
        val actual = measureTimedValue {
            ContextSelector(fixture.merge(), fixture.estimator, fixture.graph)
                .select(fixture.input, offers, TokenCount(16000))
        }

        assertEquals(frozen.value, actual.value)
        println(
            "selector-large v1Ms=${frozen.duration.inWholeMilliseconds} exactMs=${actual.duration.inWholeMilliseconds}",
        )
    }

    @Test
    fun `frozen outputs retain generated provenance contained spans warnings and safe fences`() {
        val fixture = MixedSelectorFixture()
        for (level in ContextLevel.entries) {
            val offers = fixture.offers(level)
            for (budget in listOf(1, 80, 250, 1000, 2000)) {
                val frozen = FrozenSelectorV1(fixture.merge(), fixture.estimator, fixture.graph)
                    .select(fixture.input, offers, TokenCount(budget))
                val actual = ContextSelector(fixture.merge(), fixture.estimator, fixture.graph)
                    .select(fixture.input, offers, TokenCount(budget))

                assertEquals(frozen, actual, "level=$level budget=$budget")
                assertEquals(
                    ContextMarkdown.renderContents(fixture.input.task, frozen.fragments, fixture.input.unresolved),
                    ContextMarkdown.renderContents(fixture.input.task, actual.fragments, fixture.input.unresolved),
                )
            }
        }
    }

    @Test
    fun `exact selector outputs match the frozen algorithm across mixed files symbols and Unicode`() {
        val fixture = SelectorPerformanceFixture(3, 4)
        for (level in ContextLevel.entries) {
            val offers = fixture.offers(level)
            for (budget in listOf(1, 80, 250, 1000, 2000)) {
                val frozen = FrozenSelectorV1(fixture.merge(), fixture.estimator, fixture.graph)
                    .select(fixture.input, offers, TokenCount(budget))
                val actual = ContextSelector(fixture.merge(), fixture.estimator, fixture.graph)
                    .select(fixture.input, offers, TokenCount(budget))

                assertEquals(frozen, actual, "level=$level budget=$budget")
            }
        }
    }

    @Test
    fun `mixed file and declaration scale avoids most repeated whole package tokenizations`() {
        val fixture = SelectorPerformanceFixture(16, 6)
        val offers = fixture.offers()
        val frozenCounter = CountingTokenEstimator(fixture.estimator)
        val exactCounter = CountingTokenEstimator(fixture.estimator)
        val frozen = measureTimedValue {
            FrozenSelectorV1(fixture.merge(), frozenCounter, fixture.graph)
                .select(fixture.input, offers, TokenCount(2000))
        }
        val actual = measureTimedValue {
            ContextSelector(fixture.merge(), exactCounter, fixture.graph)
                .select(fixture.input, offers, TokenCount(2000))
        }

        assertEquals(frozen.value, actual.value)
        assertTrue(
            exactCounter.calls < frozenCounter.calls / 2,
            "v1=${frozenCounter.calls} exact=${exactCounter.calls}",
        )
        println(
            "selector-scale v1Calls=${frozenCounter.calls} exactCalls=${exactCounter.calls} " +
                "v1Ms=${frozen.duration.inWholeMilliseconds} exactMs=${actual.duration.inWholeMilliseconds}",
        )
    }
}
