package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.TextIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorRouteScannerTest {
    @Test
    fun `every method of the routing DSL with a literal path is a route`() {
        val code = """
            fun Route.all() {
                get("/a") { }
                post("/b") { }
                put("/c") { }
                delete("/d") { }
                patch("/e") { }
                head("/f") { }
                options("/g") { }
            }
        """.trimIndent()

        assertEquals(
            listOf("GET /a", "POST /b", "PUT /c", "DELETE /d", "PATCH /e", "HEAD /f", "OPTIONS /g"),
            routeNames(code),
        )
    }

    @Test
    fun `route blocks give the prefix of the routes that they enclose, however deep`() {
        val code = """
            fun Route.api() {
                route("/api") {
                    route("/v1") {
                        get("/tasks") { }
                        post("/tasks") { }
                    }
                    get("/health") { }
                }
                get("/outside") { }
            }
        """.trimIndent()

        assertEquals(
            listOf("GET /api/v1/tasks", "POST /api/v1/tasks", "GET /api/health", "GET /outside"),
            routeNames(code),
        )
    }

    @Test
    fun `a method with no path inside a route block is a route at the path of the block`() {
        val code = """
            fun Route.tasks() {
                route("/tasks/{id}") {
                    get { call.respondText("one") }
                    put { }
                    delete { }
                }
            }
        """.trimIndent()

        assertEquals(listOf("GET /tasks/{id}", "PUT /tasks/{id}", "DELETE /tasks/{id}"), routeNames(code))
    }

    @Test
    fun `a method with no path outside a route block is no route`() {
        val code = """
            fun Application.module() {
                routing {
                    get { }
                }
                val result = get { 1 }
            }
        """.trimIndent()

        assertEquals(emptyList(), routeNames(code))
    }

    @Test
    fun `blocks that give no path, such as a guard, let the prefix through`() {
        val code = """
            fun Application.module() {
                routing {
                    authenticate("session") {
                        route("/admin") {
                            rateLimit {
                                get("/users") { }
                            }
                        }
                    }
                    get("/public") { }
                }
            }
        """.trimIndent()

        assertEquals(listOf("GET /admin/users", "GET /public"), routeNames(code))
    }

    @Test
    fun `paths are joined with single slashes, a leading slash and no trailing slash but for the root`() {
        val code = """
            fun Route.paths() {
                route("/api/") {
                    get("/tasks/") { }
                    get("") { }
                    get("/") { }
                    get("nested//path") { }
                }
                route("shop") { get("items") { } }
                get("/") { }
                get("") { }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "GET /api/tasks",
                "GET /api",
                "GET /api",
                "GET /api/nested/path",
                "GET /shop/items",
                "GET /",
                "GET /",
            ),
            routeNames(code),
        )
    }

    @Test
    fun `a route with a method selector keeps the prefix for what it encloses`() {
        val code = """
            fun Route.selected() {
                route("/x", HttpMethod.Post) {
                    get("/y") { }
                    handle { }
                }
            }
        """.trimIndent()

        assertEquals(listOf("GET /x/y"), routeNames(code))
    }

    @Test
    fun `a route is located at its call, from the name to the end of its block`() {
        val code = ROUTING_IMPORT + "fun Route.tasks() {\n    get(\"/tasks\") {\n        call.respond(1)\n    }\n}\n"
        val route = KtorRouteScanner(code).scan().single()
        val index = TextIndex(code)
        val name = index.range(route.nameSpan)

        assertEquals("GET /tasks", route.name)
        assertEquals("GET", route.method)
        assertEquals("/tasks", route.path)
        assertEquals(3, name.startLine)
        assertEquals(5 to 3, name.startColumn to name.endColumn - name.startColumn)
        assertEquals(3 to 5, index.range(route.declaration).let { it.startLine to it.endLine })
        assertEquals("get", code.substring(route.nameSpan.start, route.nameSpan.end))
    }

    @Test
    fun `a file that is cut off still gives the routes before the cut`() {
        val code = "fun Route.cut() {\n    route(\"/a\") {\n        get(\"/b\") {\n"

        assertEquals(listOf("GET /a/b"), routeNames(code))
    }

    @Test
    fun `the same route declared twice is found twice`() {
        val code = "fun Route.twice() { get(\"/a\") { }\n authenticate { get(\"/a\") { } } }\n"

        assertEquals(listOf("GET /a", "GET /a"), routeNames(code))
    }
}
