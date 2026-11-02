package io.github.krosstack.kscope.gradle.model

import java.io.Serializable

internal const val SCHEMA_VERSION = 1

/** The tooling model of one Gradle project: a single schema-versioned JSON document. */
interface KscopeModel {
    val json: String
}

class DefaultKscopeModel(override val json: String) :
    KscopeModel,
    Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}
