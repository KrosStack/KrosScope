package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.RegisteredRepository
import java.nio.file.Path

/**
 * What the user registered with one workspace: repositories from elsewhere, and the folders of built archives, in the
 * order they are looked in.
 */
internal data class RegisteredWorkspace(
    val repositories: List<RegisteredRepository> = emptyList(),
    val artifactFolders: List<Path> = emptyList(),
) {
    val isEmpty: Boolean get() = repositories.isEmpty() && artifactFolders.isEmpty()
}
