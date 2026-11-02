package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.model.ModuleTrait

/** The traits of a module, derived from the ids of the plugins applied to it. */
internal object ModuleTraits {
    private val byPlugin = mapOf(
        "com.android.application" to ModuleTrait.ANDROID_APPLICATION,
        "com.android.library" to ModuleTrait.ANDROID_LIBRARY,
        "com.android.kotlin.multiplatform.library" to ModuleTrait.ANDROID_LIBRARY,
        "org.jetbrains.kotlin.multiplatform" to ModuleTrait.KOTLIN_MULTIPLATFORM,
        "org.jetbrains.kotlin.jvm" to ModuleTrait.KOTLIN_JVM,
        "org.jetbrains.kotlin.android" to ModuleTrait.KOTLIN_ANDROID,
        "java" to ModuleTrait.JAVA,
        "java-library" to ModuleTrait.JAVA,
    )

    fun of(document: ProjectDocument): Set<ModuleTrait> {
        val builtInKotlin = listOfNotNull(
            ModuleTrait.KOTLIN_ANDROID.takeIf { document.android?.isDsl == true && document.kotlin != null },
            ModuleTrait.BUILD_LOGIC.takeIf { document.build.included },
        )
        return ofPlugins(document.plugins) + builtInKotlin
    }

    fun ofPlugins(pluginIds: Collection<String>): Set<ModuleTrait> = pluginIds.mapNotNull(byPlugin::get).toSet()
}
