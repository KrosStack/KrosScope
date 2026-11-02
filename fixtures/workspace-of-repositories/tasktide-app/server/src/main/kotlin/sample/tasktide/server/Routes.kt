package sample.tasktide.server

import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.taskRoutes(tasks: TaskStore) {
    get("/tasks") {
        call.respond(tasks.newestFirst())
    }
    staticResources("/", "static")
}
