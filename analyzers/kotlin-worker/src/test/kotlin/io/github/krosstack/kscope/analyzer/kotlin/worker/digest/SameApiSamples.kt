package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

internal data class ApiMutation(val name: String, val before: String, val after: String)

internal object SameApiSamples {
    val source = """
        package api
        interface Runner { fun run(): Int }
        class Box(val seed: Int) {
            var value: Int = seed
                get() { return field }
                set(newValue) { field = newValue }
            init { value = seed }
            constructor(): this(1) { value = 2 }
            fun block(input: Int): Int { return input + value }
        }
        fun body(value: Int): Int { return value + 1 }
        fun expression(value: Int) = value + 1
        fun selected(value: Int) = value
        fun selected(value: String) = value.length
        val property = expression(2)
        const val constant = 1
        val implementation = object : Runner { override fun run(): Int { return 1 } }
    """.trimIndent()

    fun mutations(): List<ApiMutation> = listOf(
        mutation("function statement", "return value + 1", "return value + 2"),
        mutation("later declarations shift down", "return value + 1", "val next = value + 1\n\n\n\nreturn next"),
        mutation("getter and setter", "return field", "return field + 1")
            .let { it.copy(after = it.after.replace("field = newValue", "field = newValue + 1")) },
        mutation("class initializer", "value = seed", "value = seed + 1"),
        mutation("secondary constructor", "value = 2", "value = 3"),
        mutation("object expression member", "return 1", "return 2"),
        ApiMutation("comments KDoc and whitespace", source, "/** API */\n" + source.replace(" ", "  /* gap */ ")),
        mutation("local declarations", "return value + 1", "class Local\nfun local() = value\nreturn local() + 1"),
        mutation("documentation of a referenced declaration", "fun body(", "/** Adds one. */\nfun body("),
    )

    private fun mutation(name: String, before: String, after: String) =
        ApiMutation(name, source, source.replace(before, after))
}
