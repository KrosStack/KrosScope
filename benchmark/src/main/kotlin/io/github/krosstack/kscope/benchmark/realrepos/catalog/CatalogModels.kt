package io.github.krosstack.kscope.benchmark.realrepos.catalog

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogRepositoryState(
    val name: String,
    val baseCommit: String,
    val scenarioCommit: String,
    val solutionCommit: String,
    val gold: List<String> = emptyList(),
)

@Serializable
internal data class CatalogTask(
    val id: String,
    val prompt: String,
    val repositories: List<CatalogRepositoryState>,
    val level: Int? = null,
    val named: Boolean? = null,
)

@Serializable
internal data class ExternalCatalog(val schemaVersion: Int, val repository: String, val tasks: List<CatalogTask>)

@Serializable
internal data class FreezeCatalogEntry(val file: String, val sha256: String)

@Serializable
internal data class FreezeRepository(val id: String, val baseCommit: String)

@Serializable
internal data class CatalogFreezeFile(
    val id: String,
    val catalog: FreezeCatalogEntry,
    val repositories: List<FreezeRepository>,
    val schemaVersion: Int = 1,
)
