package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.progress.stage
import kotlin.time.measureTimedValue

internal class StageTimer {
    private val millis = linkedMapOf<String, Long>()

    fun <T> time(stage: String, block: () -> T): T {
        val (value, elapsed) = measureTimedValue(block)
        millis.merge(stage, elapsed.inWholeMilliseconds, Long::plus)
        return value
    }

    fun snapshot(): Map<String, Long> = LinkedHashMap(millis)
}
