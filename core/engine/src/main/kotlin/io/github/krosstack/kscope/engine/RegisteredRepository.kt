package io.github.krosstack.kscope.engine

import java.nio.file.Path

/**
 * A repository that the user registered with a workspace from anywhere on disk: its checkout [root], and the [name]
 * its files are printed under, as the directory of a checkout below the root would be.
 */
public data class RegisteredRepository(public val name: String, public val root: Path)
