package io.github.krosstack.kscope.benchmark.realrepos.catalog

import java.security.MessageDigest
import java.util.HexFormat

internal object CatalogFreeze {
    fun verify(catalogBytes: ByteArray, freeze: CatalogFreezeFile, catalog: ExternalCatalog) {
        require(freeze.schemaVersion == 1) { "Unsupported freeze file." }
        require(catalog.schemaVersion == 2) { "Unsupported catalog file: schemaVersion ${catalog.schemaVersion}." }
        require(digest(catalogBytes) == freeze.catalog.sha256.lowercase()) {
            "Catalog differs from the freeze file ${freeze.id}."
        }
        val frozen = freeze.repositories.associate { it.id to it.baseCommit }
        catalog.tasks.flatMap { it.repositories }.forEach { state ->
            require(frozen[state.name] == state.baseCommit) {
                "Repository ${state.name} is not frozen at its base commit."
            }
        }
    }

    fun digest(bytes: ByteArray): String {
        val text = String(bytes, Charsets.UTF_8).replace("\r\n", "\n")
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    }
}
