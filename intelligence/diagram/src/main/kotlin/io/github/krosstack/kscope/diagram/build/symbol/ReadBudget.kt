package io.github.krosstack.kscope.diagram.build.symbol

internal const val READER_CALL_BUDGET: Int = 1_200

internal class ReadBudget(private val limit: Int) {
    private var used = 0

    var exhausted: Boolean = false
        private set

    val remaining: Int get() = limit - used

    fun take(): Boolean {
        val available = used < limit
        if (available) used += 1 else exhausted = true
        return available
    }
}
