package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms

/**
 * The platform a module is analysed as. The classpath of a common module is the one of the JVM and Android
 * compilations that include it, so its libraries hold Java classes, which only a session with Java support can read:
 * a common module with a classpath is analysed as JVM code.
 */
internal fun WorkerModule.analysisPlatform(): Platform =
    if (platform == Platform.COMMON && classpath.isNotEmpty()) Platform.JVM else platform

internal fun Platform.toTargetPlatform(): TargetPlatform = when (this) {
    Platform.COMMON -> CommonPlatforms.defaultCommonPlatform
    Platform.JVM, Platform.ANDROID -> JvmPlatforms.defaultJvmPlatform
    Platform.NATIVE -> NativePlatforms.unspecifiedNativePlatform
    Platform.JS -> JsPlatforms.defaultJsPlatform
    Platform.WASM -> WasmPlatforms.Default
}

/** Without the multiplatform feature the Analysis API cannot pair an `actual` declaration with its `expect`. */
internal fun multiplatformLanguageSettings(): LanguageVersionSettings = LanguageVersionSettingsImpl(
    LanguageVersion.LATEST_STABLE,
    ApiVersion.LATEST_STABLE,
    emptyMap(),
    mapOf(LanguageFeature.MultiPlatformProjects to LanguageFeature.State.ENABLED),
)
