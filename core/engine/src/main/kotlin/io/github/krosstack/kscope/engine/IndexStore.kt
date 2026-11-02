package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.StoreOpenResult

internal object IndexStore {
    /** The index is derived data: a file in another schema version is deleted and created anew. */
    fun open(files: IndexFiles): OpenedIndex {
        files.createDirectory()
        return when (val first = SqliteGraphStore.open(files.database)) {
            is StoreOpenResult.Opened -> OpenedIndex(first.store, emptyList())

            is StoreOpenResult.Incompatible -> {
                files.deleteDerivedData()
                OpenedIndex(openFresh(files), listOf(rebuiltNotice(first)))
            }
        }
    }

    private fun openFresh(files: IndexFiles): SqliteGraphStore =
        when (val result = SqliteGraphStore.open(files.database)) {
            is StoreOpenResult.Opened -> result.store
            is StoreOpenResult.Incompatible -> error("A new index cannot be incompatible: $result")
        }

    private fun rebuiltNotice(incompatible: StoreOpenResult.Incompatible) = Diagnostic(
        severity = DiagnosticSeverity.INFO,
        code = ReasonCode("index.rebuilt"),
        message = "The index used schema version ${incompatible.foundVersion} but ${incompatible.supportedVersion} " +
            "is required; it was discarded and rebuilt.",
        location = null,
    )
}
