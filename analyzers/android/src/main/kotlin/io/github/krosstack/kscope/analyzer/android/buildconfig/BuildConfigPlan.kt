package io.github.krosstack.kscope.analyzer.android.buildconfig

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.SourceSet

/** The `BuildConfig` class of one module. [script] is the build script that declares the fields. */
internal class BuildConfigPlan(
    val module: Module,
    val namespace: String,
    val host: SourceSet,
    val script: Artifact,
    val fields: List<ConfigField>,
) {
    val className: String get() = "$namespace.$CLASS_NAME"

    companion object {
        const val CLASS_NAME = "BuildConfig"
        const val CLASS_SIGNATURE = "public final class BuildConfig"
    }
}

/** The text of the Java stub of `BuildConfig`. */
internal object BuildConfigStubText {
    private const val INDENT = "    "

    fun render(plan: BuildConfigPlan): String {
        val fields = plan.fields.joinToString("\n") { "$INDENT${it.signature} = ${it.value};" }
        return "package ${plan.namespace};\n\n${BuildConfigPlan.CLASS_SIGNATURE} {\n$fields\n}\n"
    }
}
