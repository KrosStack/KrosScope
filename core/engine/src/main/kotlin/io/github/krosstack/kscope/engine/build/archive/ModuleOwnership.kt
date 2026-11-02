package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.BuildModel

/** Which module a node of a dependency belongs to, and which repository a module is built in. */
internal class ModuleOwnership(model: BuildModel) {
    private val repositoryOfRoot = model.buildRoots.associate { it.id to it.repository }
    private val modules = model.modules.associateBy { it.id }
    private val moduleOfSourceSet = model.sourceSets.associate { it.id to it.module }

    fun moduleOf(node: NodeRef): Module? = when (node) {
        is NodeRef.OfModule -> modules[node.id]
        is NodeRef.OfSourceSet -> moduleOfSourceSet[node.id]?.let(modules::get)
        is NodeRef.OfSymbol, is NodeRef.OfArtifact, is NodeRef.OfExternal -> null
    }

    fun repositoryOf(module: Module): RepositoryId? = repositoryOfRoot[module.buildRoot]
}
