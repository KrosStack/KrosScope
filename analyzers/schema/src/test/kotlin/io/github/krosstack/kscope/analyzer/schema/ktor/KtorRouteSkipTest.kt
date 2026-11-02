package io.github.krosstack.kscope.analyzer.schema.ktor

import kotlin.test.Test
import kotlin.test.assertEquals

class KtorRouteSkipTest {
    @Test
    fun `comments and strings are never taken for code`() {
        val code = "" +
            "fun Route.shown() {\n" +
            "    // get(\"/line\") { }\n" +
            "    /* get(\"/block\") { } /* get(\"/nested\") { } */ get(\"/still-comment\") { } */\n" +
            "    val text = \"get(\\\"/string\\\") { }\"\n" +
            "    val raw = \"\"\"get(\"/raw\") { }\"\"\"\n" +
            "    val template = \"\${\"get(\\\"/inner\\\") { }\"}\"\n" +
            "    val char = '\"'\n" +
            "    get(\"/real\") { }\n" +
            "}\n"

        assertEquals(listOf("GET /real"), routeNames(code))
    }

    @Test
    fun `typed resource calls and calls with a handler argument are skipped`() {
        val code = """
            fun Route.typed() {
                get<Tasks> { }
                get<Tasks.Id>("/x") { }
                post("/y", handler)
                put("/z", ::update)
                delete("/w") { }
            }
        """.trimIndent()

        assertEquals(listOf("DELETE /w"), routeNames(code))
    }

    @Test
    fun `a path that is not a plain literal is skipped`() {
        val code = """
            fun Route.built(id: String) {
                get(PATH) { }
                get("/a/${'$'}id") { }
                get("/b/${'$'}{id}") { }
                get("/c" + id) { }
                get(""${'"'}/raw""${'"'}) { }
                get("/ok") { }
            }
        """.trimIndent()

        assertEquals(listOf("GET /ok"), routeNames(code))
    }

    @Test
    fun `a route block with a built path hides what it encloses`() {
        val code = """
            fun Route.built(prefix: String) {
                route(prefix) {
                    get("/hidden") { }
                }
                route("/ok") {
                    route("/p/${'$'}prefix") { get("/deeper") { } }
                    route(base + "/x") { get("/deeper") { } }
                    get("/kept") { }
                }
            }
        """.trimIndent()

        assertEquals(listOf("GET /ok/kept"), routeNames(code))
    }

    @Test
    fun `a call with a receiver is no route`() {
        val code = """
            suspend fun check(client: HttpClient) {
                client.get("/remote") { header("a", "b") }
                this.post("/own") { }
                call.parameters.get("id")
                routing?.delete("/safe") { }
                get("/own-route") { }
            }
        """.trimIndent()

        assertEquals(listOf("GET /own-route"), routeNames(code))
    }

    @Test
    fun `a file that does not import from the routing package is not read`() {
        val code = "fun Route.tasks() { get(\"/tasks\") { } }\n"

        assertEquals(emptyList(), routeNames(code, header = ""))
        assertEquals(emptyList(), routeNames(code, header = "import io.ktor.server.application.*\n"))
        assertEquals(emptyList(), routeNames(code, header = "// import io.ktor.server.routing.*\n"))
        assertEquals(emptyList(), routeNames(code, header = "val s = \"import io.ktor.server.routing.*\"\n"))
        assertEquals(emptyList(), routeNames(code, header = "import io.ktor.server.routingextras.*\n"))
    }

    @Test
    fun `any import from the routing package is enough, a single name or the whole package`() {
        val code = "fun Route.tasks() { get(\"/tasks\") { } }\n"

        assertEquals(listOf("GET /tasks"), routeNames(code, header = "import io.ktor.server.routing.Route\n"))
        assertEquals(listOf("GET /tasks"), routeNames(code, header = "import io.ktor.server.routing.get\n"))
        assertEquals(
            listOf("GET /tasks"),
            routeNames(code, header = "package a.b\n\nimport io.ktor.server.routing.*\n"),
        )
    }
}
