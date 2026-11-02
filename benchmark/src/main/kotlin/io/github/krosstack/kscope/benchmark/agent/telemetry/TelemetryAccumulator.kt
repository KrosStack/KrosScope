package io.github.krosstack.kscope.benchmark.agent.telemetry

internal class TelemetryAccumulator(private val context: ParseContext) {
    private val toolCalls = sortedMapOf<String, Int>()
    private val unknownTypes = sortedMapOf<String, Int>()
    private val read = sortedSetOf<String>()
    private val edited = sortedSetOf<String>()
    private var firstGoldRead: Milestone? = null
    private var firstGoldEdit: Milestone? = null
    private val root = context.workspaceRoot?.let(::slashed)?.trimEnd('/')

    var toolErrors = 0
        private set
    var bytesRead = 0L
        private set
    var events = 0
        private set

    fun event() {
        events++
    }

    fun unknownEvent(type: String) {
        unknownTypes.merge(type, 1, Int::plus)
    }

    fun toolCall(name: String) {
        toolCalls.merge(name, 1, Int::plus)
    }

    fun toolError() {
        toolErrors++
    }

    fun bytesRead(count: Long) {
        bytesRead += count
    }

    fun fileRead(rawPath: String, line: TimedLine) {
        val path = relativePath(rawPath)
        read += path
        if (firstGoldRead == null && path in context.goldFiles) firstGoldRead = Milestone(line.index, line.elapsedMs)
    }

    fun fileEdited(rawPath: String, line: TimedLine) {
        val path = relativePath(rawPath)
        edited += path
        if (firstGoldEdit == null && path in context.goldFiles) firstGoldEdit = Milestone(line.index, line.elapsedMs)
    }

    fun build(base: AgentTelemetry): AgentTelemetry = base.copy(
        toolCalls = toolCalls.toMap(),
        toolErrors = toolErrors,
        filesRead = read.toList(),
        bytesRead = bytesRead,
        filesEdited = edited.toList(),
        firstGoldRead = firstGoldRead,
        firstGoldEdit = firstGoldEdit,
        events = events,
        unknownEventTypes = unknownTypes.toMap(),
    )

    fun relativePath(rawPath: String): String {
        val path = slashed(rawPath)
        val inside = root?.takeIf { path.startsWith("$it/", ignoreCase = true) }?.let { path.substring(it.length + 1) }
        return inside ?: knownSuffix(path) ?: if (isAbsolute(path)) OUTSIDE else path.removePrefix("./")
    }

    private fun isAbsolute(path: String): Boolean = path.startsWith("/") || path.contains(":/")

    private fun knownSuffix(path: String): String? =
        context.knownFiles.filter { path.endsWith("/$it") }.maxByOrNull { it.length }

    companion object {
        const val OUTSIDE = "<outside-workspace>"

        fun slashed(path: String): String = path.replace('\\', '/')
    }
}
