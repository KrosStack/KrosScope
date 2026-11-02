package io.github.krosstack.kscope.analyzer.jni.loads

/**
 * The lexical differences between Kotlin and Java that decide where a string or a comment ends: Kotlin nests block
 * comments and has string templates, and Java reads escapes in text blocks where Kotlin reads raw strings.
 */
internal enum class JvmDialect(
    val nestsComments: Boolean,
    val hasTemplates: Boolean,
    val escapesInTextBlocks: Boolean,
) {
    KOTLIN(nestsComments = true, hasTemplates = true, escapesInTextBlocks = false),
    JAVA(nestsComments = false, hasTemplates = false, escapesInTextBlocks = true),
}
