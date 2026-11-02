package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.graph.StoredUnit
import java.nio.file.Files
import java.nio.file.Path

internal fun canonicalJson(batches: List<FactBatch>): String = CanonicalFacts.toJson(batches)

internal fun GraphStore.replaceAll(batches: List<FactBatch>) = batches.forEach(::replace)

internal fun FactBatch.stored() = StoredUnit(producer, owner, inputFingerprint)

internal fun openedStore(file: Path): GraphStore = when (val result = SqliteGraphStore.open(file)) {
    is StoreOpenResult.Opened -> result.store
    is StoreOpenResult.Incompatible -> error("Unexpected $result")
}

internal class TempDirectory : AutoCloseable {
    val path: Path = Files.createTempDirectory("kscope-store")

    override fun close() {
        path.toFile().deleteRecursively()
    }
}
