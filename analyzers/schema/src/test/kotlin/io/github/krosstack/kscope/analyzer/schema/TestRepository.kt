package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId

internal object TestRepository {
    val id = RepositoryId("repo")
    val buildRoot = BuildRootId("repo//")

    fun moduleId(path: String) = ModuleId("repo//$path")

    fun sourceSetId(path: String, name: String) = SourceSetId("repo//$path@$name")
}
