package io.github.krosstack.kscope.analyzer.schema.stub

private const val INDENT = "    "

/** The Kotlin source of a stub: public signatures with placeholder bodies, as the analyzer reads them. */
internal object StubText {
    fun render(file: StubFile): String {
        val header = if (file.packageName.isEmpty()) "" else "package ${packageText(file.packageName)}\n\n"
        return header + file.classes.joinToString("\n\n") { classText(it, "") } + "\n"
    }

    private fun packageText(packageName: String) = packageName.split('.').joinToString(".") { StubTypes.written(it) }

    private fun classText(stub: StubClass, indent: String): String {
        val name = if (stub.form == StubForm.COMPANION) "" else " ${StubTypes.written(stub.name)}"
        val constructor = stub.constructor?.let { constructorText(it, indent) }.orEmpty()
        val superTypes = stub.superTypes?.let { " : $it" }.orEmpty()
        val head = "${indent}public ${stub.form.keyword}$name$constructor$superTypes"
        val body = bodyOf(stub, "$indent$INDENT")
        return if (body.isEmpty()) head else "$head {\n${body.joinToString("\n\n")}\n$indent}"
    }

    private fun constructorText(parameters: List<StubParameter>, indent: String): String {
        val lines = parameters.joinToString("") { "$indent$INDENT${parameterText(it)},\n" }
        return "(\n$lines$indent)"
    }

    private fun parameterText(parameter: StubParameter): String {
        val modifiers = when {
            parameter.isOverride -> "override val "
            parameter.isProperty -> "public val "
            else -> ""
        }
        val default = parameter.default?.let { " = $it" }.orEmpty()
        return "$modifiers${StubTypes.written(parameter.name)}: ${parameter.type}$default"
    }

    private fun bodyOf(stub: StubClass, indent: String): List<String> {
        val entries = entriesText(stub.entries, indent)
        val members = stub.members.map { "$indent${memberText(it)}" }
        return listOfNotNull(entries) + members + stub.nested.map { classText(it, indent) }
    }

    private fun entriesText(entries: List<StubEntry>, indent: String): String? =
        entries.takeIf { it.isNotEmpty() }?.joinToString(",\n", postfix = ";") {
            "$indent${StubTypes.written(it.name)}(${it.arguments})"
        }

    private fun memberText(member: StubMember): String = when (member) {
        is StubProperty -> propertyText(member)
        is StubFunction -> functionText(member)
    }

    private fun propertyText(property: StubProperty): String {
        val initializer = property.initializer?.let { " = $it" }.orEmpty()
        return "public val ${StubTypes.written(property.name)}: ${property.type}$initializer"
    }

    private fun functionText(function: StubFunction): String {
        val operator = if (function.isOperator) "operator " else ""
        val typeParameters = function.typeParameters?.let { "<$it> " }.orEmpty()
        val parameters = function.parameters.joinToString(", ") { parameterText(it) }
        val body = if (function.hasBody) " = $STUB_BODY" else ""
        val name = StubTypes.written(function.name)
        return "public ${operator}fun $typeParameters$name($parameters): ${function.returnType}$body"
    }
}
