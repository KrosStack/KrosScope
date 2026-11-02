package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

internal object DifferentApiSamples {
    fun mutations(): List<ApiMutation> = signatures() + values() + keptBodies()

    private fun signatures(): List<ApiMutation> = listOf(
        pair("parameter type", "fun f(x: Int) {}", "fun f(x: Long) {}"),
        pair("return type", "fun f(): Int { return 1 }", "fun f(): Long { return 1 }"),
        pair("name", "fun f() {}", "fun g() {}"),
        pair("visibility", "public fun f() {}", "private fun f() {}"),
        pair("modifier", "fun f() {}", "suspend fun f() {}"),
        pair("added declaration", "fun f() {}", "fun f() {}\nfun g() {}"),
        pair("removed declaration", "fun f() {}\nfun g() {}", "fun f() {}"),
        pair("supertype", "class C : A()", "class C : B()"),
        pair("import", "import a.A\nfun f() {}", "import b.A\nfun f() {}"),
    )

    private fun values(): List<ApiMutation> = listOf(
        pair("expression body", "fun f() = 1", "fun f() = 2"),
        pair("property initializer", "val p = 1", "val p = 2"),
        pair("constant initializer", "const val p = 1", "const val p = 2"),
        pair("delegate", "val p by lazy { 1 }", "val p by lazy { 2 }"),
        pair("default value", "fun f(x: Int = 1) {}", "fun f(x: Int = 2) {}"),
        pair("annotation argument", "@A(1) fun f() {}", "@A(2) fun f() {}"),
        pair("lambda", "val p = { 1 }", "val p = { 2 }"),
        pair("type alias", "typealias T = Int", "typealias T = Long"),
        pair("enum entry", "enum class E { A }", "enum class E { B }"),
    )

    private fun keptBodies(): List<ApiMutation> = listOf(
        pair("inline function", "inline fun f() { a() }", "inline fun f() { b() }"),
        pair("contract", "fun f() { contract { a() }; a() }", "fun f() { contract { a() }; b() }"),
        pair("qualified contract", "fun f() { p.contract { a() }; a() }", "fun f() { p.contract { a() }; b() }"),
        pair("anonymous function", "val p = fun() { a() }", "val p = fun() { b() }"),
        pair(
            "constructor delegation",
            "class C { constructor(): this(1) {} }",
            "class C { constructor(): this(2) {} }",
        ),
        pair("inline accessor", "val p: Int inline get() { return 1 }", "val p: Int inline get() { return 2 }"),
        pair("inline property", "inline val p: Int get() { return 1 }", "inline val p: Int get() { return 2 }"),
        pair(
            "contract in an accessor",
            "val p: Boolean get() { contract { a() }; return a() }",
            "val p: Boolean get() { contract { a() }; return b() }",
        ),
        pair(
            "contract under an import alias",
            "import kotlin.contracts.contract as c\nfun f() { c { a() }; a() }",
            "import kotlin.contracts.contract as c\nfun f() { c { a() }; b() }",
        ),
    )

    private fun pair(name: String, before: String, after: String) = ApiMutation(name, before, after)
}
