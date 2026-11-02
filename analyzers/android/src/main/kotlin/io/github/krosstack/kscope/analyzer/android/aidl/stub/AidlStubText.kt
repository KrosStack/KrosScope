package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.signature

private const val INDENT = "    "

/** The Java source of a planned stub: signatures with the smallest bodies that make the file well formed. */
internal object AidlStubText {
    fun render(packageName: String?, type: StubType): String {
        val header = packageName?.let { "package $it;\n\n" }.orEmpty()
        return header + lines(type).joinToString("\n") + "\n"
    }

    private fun lines(member: StubMember): List<String> = when (member) {
        is StubType -> typeLines(member)
        is StubMethod -> listOf(member.signature + (member.body?.let { " $it" } ?: ";"))
        is StubField -> listOf(member.signature + member.initializer?.let { " = $it" }.orEmpty() + ";")
    }

    private fun typeLines(type: StubType): List<String> {
        val members = type.members.flatMap(::lines).map { INDENT + it }
        return listOf("${type.signature} {") + members + "}"
    }
}
