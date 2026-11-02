package io.github.krosstack.kscope.analyzer.android.rclass

/** The Java source of the signature-only stub of `R`: nested static classes with `public static final` fields. */
internal object JavaStubText {
    private const val INDENT = "    "

    fun render(plan: RClassPlan): String {
        val classes = plan.types.map(::typeText)
        val body = classes.joinToString(separator = "\n\n")
        val members = if (classes.isEmpty()) "" else "$body\n"
        return "package ${plan.namespace};\n\npublic final class R {\n$members}\n"
    }

    private fun typeText(type: RType): String {
        val fields = type.fields.joinToString(separator = "") { "$INDENT$INDENT${fieldText(it)}\n" }
        return "${INDENT}public static final class ${type.name} {\n$fields$INDENT}"
    }

    private fun fieldText(field: RField): String = if (field.isArray) {
        "public static final int[] ${field.name} = {};"
    } else {
        "public static final int ${field.name} = 0;"
    }
}
