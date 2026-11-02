package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.gold.GoldRelationships
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

/** Reads the gold relationships of the workspace of repositories, which sit in `gold/relationships.json`. */
internal object WorkspaceGoldLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(goldDirectory: Path): GoldRelationships =
        json.decodeFromString(GoldRelationships.serializer(), goldDirectory.resolve(RELATIONSHIPS).readText())

    fun outputsFile(goldDirectory: Path): Path = goldDirectory.resolve(OUTPUTS)

    private const val RELATIONSHIPS = "gold/relationships.json"
    private const val OUTPUTS = "outputs.json"
}
