package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.model.SymbolId

/**
 * The ids that more than one declaration of the workspace carries. The declaration pass records one declaration per
 * id, so a relationship that names such an id cannot say which declaration it means. The ids are collected on the
 * first question, because a target may be declared in any source set of the session.
 */
internal class IdentityCollisions(collect: () -> Set<SymbolId>) {
    private val shared by lazy(collect)

    fun isShared(id: SymbolId): Boolean = id in shared
}
