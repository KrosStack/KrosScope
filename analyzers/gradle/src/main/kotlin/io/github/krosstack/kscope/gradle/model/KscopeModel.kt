package io.github.krosstack.kscope.gradle.model

/** The client side of the tooling model that the injected plugin builds; the plugin ships an equal interface. */
internal interface KscopeModel {
    val json: String
}
