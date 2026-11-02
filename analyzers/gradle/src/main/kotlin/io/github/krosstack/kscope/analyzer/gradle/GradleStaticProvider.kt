package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.groovy.GroovyScriptOutliner
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptOutlines
import io.github.krosstack.kscope.analyzer.gradle.statics.StaticBuild
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.ScriptOutliner
import java.io.IOException

/**
 * Reads the build model from the files of the workspace without running anything: settings scripts, build scripts
 * (outlined by [kotlinScripts] for the Kotlin DSL and by [groovyScripts] for the Groovy DSL), version catalogs,
 * convention plugins of included builds (precompiled scripts, and plugin classes whose Kotlin sources [kotlinScripts]
 * outlines too), and the directory conventions of Android, Kotlin Multiplatform and JVM projects.
 *
 * Facts that come from literal declarations are exact; those derived from conventions, templates or convention plugins
 * are heuristic; what cannot be known statically is a diagnostic. The version catalog entries become symbols, and the
 * scripts that use them reference those symbols; both travel in the facts of the result.
 */
public class GradleStaticProvider(
    private val kotlinScripts: ScriptOutliner,
    private val groovyScripts: ScriptOutliner = GroovyScriptOutliner(),
) : BuildModelProvider {
    override val producer: ProducerRef = ProducerRef("kscope.build.gradle.static", "1")

    override val requiresTrust: Boolean = false

    override fun provide(request: BuildModelRequest): BuildModelResult = try {
        StaticBuild(request, ScriptOutlines(kotlinScripts, groovyScripts, request.files)).run()
    } catch (failure: IOException) {
        val message = "A build file could not be read: ${failure.message}"
        BuildModelResult.Failed(ReasonCode("gradle.static.io-failed"), message)
    }
}
