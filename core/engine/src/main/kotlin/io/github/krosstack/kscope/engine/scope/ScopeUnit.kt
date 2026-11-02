package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Repository

/**
 * The record that an index was built with a scope that narrows a scan: an empty unit whose fingerprint is the key of
 * the scope. A scope that changes nothing leaves no unit, so an index built without settings is as it always was.
 */
internal object ScopeUnit {
    val producer = ProducerRef("kscope.settings", "1")

    fun plan(repository: Repository, scope: ScanScope): FactBatch? {
        val key = keyOf(scope) ?: return null
        return FactBatch(producer, OwnershipUnit(OwnershipKind.REPOSITORY, repository.id.value), key, emptyList())
    }

    fun keyOf(scope: ScanScope): Fingerprint? {
        if (scope.isDefault) return null
        val canonical = scope.canonical()
        return FingerprintBuilder().add(producer.id).add(producer.version)
            .add(canonical.tests.toString())
            .addList(canonical.include).addList(canonical.exclude).addList(canonical.generated)
            .build()
    }

    private fun FingerprintBuilder.addList(parts: List<String>): FingerprintBuilder =
        add(parts.size.toString()).addAll(parts)
}
