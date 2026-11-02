package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.serve.ServeServices
import io.github.krosstack.kscope.service.NotIndexedException
import io.github.krosstack.kscope.service.WorkspacePathException
import io.github.krosstack.kscope.service.map.MapJson
import io.github.krosstack.kscope.service.map.mapImpact
import io.github.krosstack.kscope.service.map.mapTask

/** The questions of the page. Whatever the service answers or fails with becomes a JSON answer, never a stack. */
internal class MapApi(private val host: MapHost) {
    fun answer(route: String, query: Map<String, String>): MapResponse = when (route) {
        "map" -> map()
        "node" -> node(query["id"])
        "impact" -> impact(query["id"], query["symbol"])
        "task" -> task(query["text"])
        "events" -> MapResponse.Events
        else -> MapResponse.error(NOT_FOUND, "Not Found")
    }

    private fun map(): MapResponse = reply(host.snapshot(MAP_TIMEOUT_MILLIS)) { snapshot ->
        versioned(snapshot, snapshot.overviewJson)
    }

    private fun node(id: String?): MapResponse {
        val snapshot = host.current
        val detail = id?.let { snapshot?.map?.detail(it) }
            ?: return MapResponse.failure(NOT_FOUND, "Not Found", "The map has no such node.")
        return runCatching { MapJson.encode(detail).toByteArray(Charsets.UTF_8) }
            .fold({ versioned(checkNotNull(snapshot), it) }) { failed(it) }
    }

    private fun impact(node: String?, symbol: String?): MapResponse {
        if (node == null && symbol == null) return bad("Name a node with id or a symbol with symbol.")
        return question { services, snapshot ->
            MapJson.encode(services.use { it.mapImpact(snapshot.map, node, symbol) })
        }
    }

    private fun task(text: String?): MapResponse = when {
        text.isNullOrBlank() -> bad("Name a task with text.")
        text.length > MAX_TASK_CHARACTERS -> bad("A task is at most $MAX_TASK_CHARACTERS characters.")
        else -> question { services, snapshot -> MapJson.encode(services.use { it.mapTask(snapshot.map, text) }) }
    }

    private fun question(ask: (ServeServices, MapSnapshot) -> String): MapResponse = reply(
        host.run(QUESTION_TIMEOUT_MILLIS) { services ->
            val snapshot = host.refresh(services)
            snapshot to ask(services, snapshot).toByteArray(Charsets.UTF_8)
        },
    ) { (snapshot, body) -> versioned(snapshot, body) }

    // Answers name nodes by their place in one map, so the page must be able to tell which map that was.
    private fun versioned(snapshot: MapSnapshot, body: ByteArray): MapResponse =
        MapResponse.json(body, mapOf(VERSION_HEADER to snapshot.version.toString()))

    private fun <T> reply(outcome: MapOutcome<T>, answer: (T) -> MapResponse): MapResponse = when (outcome) {
        is MapOutcome.Done -> answer(outcome.value)
        is MapOutcome.Failed -> failed(outcome.failure)
        MapOutcome.TimedOut -> MapResponse.failure(UNAVAILABLE, "Service Unavailable", "The index is busy; try again.")
    }

    private fun failed(failure: Throwable): MapResponse = when (failure) {
        is NotIndexedException ->
            MapResponse.failure(CONFLICT, "Conflict", "The workspace has no index. Run `kscope scan` first.")

        is WorkspacePathException -> bad(failure.message ?: "The path does not name a file of the workspace.")

        else -> MapResponse.failure(SERVER_ERROR, "Internal Server Error", "The request failed.")
    }

    private fun bad(message: String): MapResponse = MapResponse.failure(BAD_REQUEST, "Bad Request", message)

    companion object {
        const val VERSION_HEADER = "X-Map-Version"
        const val MAX_TASK_CHARACTERS = 2_000
        private const val MAP_TIMEOUT_MILLIS = 60_000L
        private const val QUESTION_TIMEOUT_MILLIS = 30_000L
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val CONFLICT = 409
        private const val SERVER_ERROR = 500
        private const val UNAVAILABLE = 503
    }
}
