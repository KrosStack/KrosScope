package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.discovery.GENERATED_DIRECTORY
import io.github.krosstack.kscope.engine.discovery.GeneratedRootProvider
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.model.GeneratedInfo

internal const val OUTPUT_DIRECTORY = "app/dist"
internal const val OUTPUT_FILE = "$OUTPUT_DIRECTORY/Out.kt"
internal const val OUTPUT_NOTES = "$OUTPUT_DIRECTORY/notes.txt"
internal const val GENERATED_FILE = "$GENERATED_DIRECTORY/com/example/app/Generated.kt"
internal const val APP_MAIN = "app/src/main/kotlin/com/example/app/Main.kt"
internal const val OUTPUTS_FILE = "app/outputs.txt"

internal val OUTPUT_LABEL = GeneratedInfo("output:bundler", null)

/** The registry with the detector of [OUTPUTS_FILE], and the structural model plus a generated root of the app. */
internal fun outputRegistry(vararg extra: Any) =
    coreRegistry(OutputsFileDetector(), *extra, providers = listOf(GeneratedRootProvider()))

internal fun TestWorkspace.declareOutputs(line: String = "$OUTPUT_DIRECTORY bundler\n") = write(OUTPUTS_FILE, line)

internal fun TestWorkspace.writeOutputs() {
    write(OUTPUT_FILE, "package com.example.app\n\nclass Out\n")
    write(OUTPUT_NOTES, "bundle")
}

internal fun TestWorkspace.commitWithIgnoredOutput() {
    write(".gitignore", "dist/\n")
    TestGit.output(root, "init", "-q")
    TestGit.output(root, "add", "-A")
    TestGit.output(root, "commit", "-q", "-m", "initial")
}
