package io.github.krosstack.kscope.analyzer.android.viewbinding

/** The Java source of the signature-only stub of a binding class: the fields and methods, without behaviour. */
internal object BindingStubText {
    private const val INDENT = "    "

    fun render(packageName: String, binding: BindingClass): String {
        val fields = binding.fields.map { "$INDENT${binding.fieldSignature(it)} = null;" }
        val methods = binding.methods().map { "$INDENT${it.signature} { return null; }" }
        val members = (fields + methods).joinToString("\n")
        return "package $packageName;\n\n${binding.classSignature} {\n$members\n}\n"
    }
}
