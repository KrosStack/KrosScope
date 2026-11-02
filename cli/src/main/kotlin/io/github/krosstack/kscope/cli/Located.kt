package io.github.krosstack.kscope.cli

import io.github.krosstack.kscope.model.WorkspaceLocations

/** A result with the [locations] that name its files by workspace path. */
internal class Located<out T>(val value: T, val locations: WorkspaceLocations)
