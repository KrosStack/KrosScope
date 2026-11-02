package io.github.krosstack.kscope.service.registry

import java.nio.file.Path

/** A repository registered with a workspace: the [name] its files print under and the [root] of its checkout. */
public data class RegisteredCheckout(public val name: String, public val root: Path)
