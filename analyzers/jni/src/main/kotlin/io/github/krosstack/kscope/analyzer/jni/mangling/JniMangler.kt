package io.github.krosstack.kscope.analyzer.jni.mangling

private const val PREFIX = "Java_"

/**
 * The names that the JNI specification gives to native functions. A name is built from the binary name of the class,
 * the method name and, for the long form, the parameter types of the JVM descriptor. In each part `/` becomes `_`,
 * `_` becomes `_1`, `;` becomes `_2`, `[` becomes `_3`, and any other character that is not an ASCII letter or digit
 * becomes `_0` and four lowercase hexadecimal digits of its UTF-16 code unit.
 */
internal object JniMangler {
    /** `Java_pkg_Outer_00024Inner_method` for the method `method` of the class with binary name `pkg/Outer$Inner`. */
    fun shortName(binaryName: String, method: String): String = "$PREFIX${escape(binaryName)}_${escape(method)}"

    /** The short name followed by `__` and the escaped parameter descriptors, such as `I[Ljava/lang/String;`. */
    fun longName(binaryName: String, method: String, parameterDescriptors: List<String>): String =
        shortName(binaryName, method) + "__" + escape(parameterDescriptors.joinToString(""))

    fun escape(text: String): String = buildString {
        text.forEach { character ->
            when {
                character == '/' || character == '.' -> append('_')
                character == '_' -> append("_1")
                character == ';' -> append("_2")
                character == '[' -> append("_3")
                character.isAsciiAlphanumeric() -> append(character)
                else -> append("_0").append(character.code.toString(HEX_RADIX).padStart(HEX_DIGITS, '0'))
            }
        }
    }

    private fun Char.isAsciiAlphanumeric() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private const val HEX_RADIX = 16
    private const val HEX_DIGITS = 4
}
