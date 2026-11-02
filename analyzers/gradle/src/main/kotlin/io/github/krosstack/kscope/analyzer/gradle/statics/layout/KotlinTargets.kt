package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.analyzer.gradle.script.allCalls
import io.github.krosstack.kscope.analyzer.gradle.script.bodies
import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** A Kotlin target. [factory] is the function that declares it, such as `iosArm64`; [name] is the target's name. */
internal class TargetSpec(val factory: String, val name: String, val platform: Platform)

private val nativeFactories = setOf(
    "iosArm64", "iosX64", "iosSimulatorArm64",
    "macosX64", "macosArm64",
    "tvosArm64", "tvosX64", "tvosSimulatorArm64",
    "watchosArm32", "watchosArm64", "watchosX64", "watchosSimulatorArm64", "watchosDeviceArm64",
    "linuxX64", "linuxArm64", "mingwX64",
    "androidNativeArm32", "androidNativeArm64", "androidNativeX86", "androidNativeX64",
)

/** Reads the targets that `kotlin { }` blocks declare, and whether they customise the source set hierarchy. */
internal object KotlinTargets {
    fun read(sources: List<ScriptSource>, hasAndroidLibraryPlugin: Boolean): List<TargetSpec> =
        sources.flatMap { source -> source.calls.bodies("kotlin") }
            .filter { it.receiver == null }
            .mapNotNull { specOf(it, hasAndroidLibraryPlugin) }
            .associateBy { it.name }.values.toList()

    /**
     * The first call that customises the hierarchy template, with the script it is in: a hierarchy template with a
     * body, a group, or a `dependsOn` that no source set block of the script owns and so cannot be attributed.
     */
    fun customHierarchy(sources: List<ScriptSource>): Pair<ScriptSource, ScriptCall>? =
        sources.firstNotNullOfOrNull { source ->
            val calls = source.calls.bodies("kotlin").allCalls()
            val custom = calls.firstOrNull(::customisesHierarchy) ?: unattributedEdge(source, calls)
            custom?.let { source to it }
        }

    private fun unattributedEdge(source: ScriptSource, calls: List<ScriptCall>): ScriptCall? {
        val attributed = SourceSetBlocks.of(source).flatMap { it.calls }
        return calls.firstOrNull { call -> call.name == "dependsOn" && attributed.none { it === call } }
    }

    private fun customisesHierarchy(call: ScriptCall) =
        call.name == "group" || (call.name == "applyHierarchyTemplate" && call.body.isNotEmpty())

    private fun specOf(call: ScriptCall, hasAndroidLibraryPlugin: Boolean): TargetSpec? = when (call.name) {
        "jvm" -> spec(call, "jvm", Platform.JVM)
        "androidTarget" -> spec(call, "android", Platform.ANDROID)
        "android", "androidLibrary" -> androidLibrary(hasAndroidLibraryPlugin)
        "js" -> spec(call, "js", Platform.JS)
        "wasmJs", "wasmWasi" -> spec(call, call.name, Platform.WASM)
        in nativeFactories -> spec(call, call.name, Platform.NATIVE)
        else -> null
    }

    private fun androidLibrary(hasAndroidLibraryPlugin: Boolean): TargetSpec? =
        TargetSpec("android", "android", Platform.ANDROID).takeIf { hasAndroidLibraryPlugin }

    private fun spec(call: ScriptCall, defaultName: String, platform: Platform) =
        TargetSpec(call.name, call.firstLiteral() ?: defaultName, platform)
}
