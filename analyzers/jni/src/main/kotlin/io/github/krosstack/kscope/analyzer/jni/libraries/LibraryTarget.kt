package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.TextRange

/** The kinds of symbol that the library targets of the native build files are. */
internal object LibraryKinds {
    const val CMAKE = "cmake.library"
    const val CARGO = "cargo.library"
    const val NDK = "ndk.library"

    val all = listOf(CMAKE, CARGO, NDK)
}

/**
 * A library that a build file declares: its [kind], its [linkage] as the file states it, and how sure that the file
 * builds a shared library with this name. A build that leaves the linkage to a variable has a [certainty] of heuristic.
 */
internal data class LibraryTarget(
    val name: String,
    val kind: String,
    val linkage: String,
    val certainty: Certainty,
    val signature: String,
    val nameRange: TextRange,
    val declaration: TextRange,
)
