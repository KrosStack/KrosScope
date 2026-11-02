package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project

/** What the plugin gathers while a project is configured and reads when the model is requested. */
internal class KscopeProjectState {
    val errors = SectionErrors()
    val variants = mutableListOf<Any>()

    companion object {
        private const val KEY = "io.github.krosstack.kscope.gradle.model.state"

        fun install(project: Project): KscopeProjectState =
            KscopeProjectState().also { project.extensions.extraProperties.set(KEY, it) }

        fun of(project: Project): KscopeProjectState {
            val extra = project.extensions.extraProperties
            return if (extra.has(KEY)) extra.get(KEY) as KscopeProjectState else install(project)
        }
    }
}
