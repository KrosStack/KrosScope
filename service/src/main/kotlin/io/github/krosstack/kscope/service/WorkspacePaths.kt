package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.WorkspaceLocations

/** How the files of the index are named for a person: by workspace path. It needs an index with files in it. */
public fun KrosScope.workspaceLocations(): WorkspaceLocations {
    memory.requireIndexed()
    return WorkspaceLocations(graph().structure().repositories)
}
