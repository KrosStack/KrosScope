package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

/** Everything gold knows about the reference workspace. */
public class GoldSet(
    public val buildModel: GoldBuildModel,
    public val relationships: GoldRelationships,
    public val closedWorld: List<GoldClosedWorldFile>,
    public val tasks: List<GoldTask>,
)

/**
 * Loads the gold data below a directory that holds `gold/build-model.json`, `gold/relationships.json`, one JSON
 * file per source file in `gold/closed-world` and one per task in `tasks`. Unknown fields are ignored, so gold may
 * grow ahead of the scorers.
 */
public object GoldLoader {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private const val GOLD = "gold"
    private const val TASKS = "tasks"
    private const val CLOSED_WORLD = "closed-world"
    private const val JSON_SUFFIX = ".json"

    /** Throws [IOException] when a file is missing and a serialization exception when one does not fit its model. */
    public fun load(directory: Path): GoldSet = GoldSet(
        buildModel = read(directory.resolve(GOLD).resolve("build-model.json")),
        relationships = read(directory.resolve(GOLD).resolve("relationships.json")),
        closedWorld = readAll(directory.resolve(GOLD).resolve(CLOSED_WORLD)),
        tasks = readAll(directory.resolve(TASKS)),
    )

    private inline fun <reified T> read(file: Path): T = json.decodeFromString<T>(file.readText())

    private inline fun <reified T> readAll(directory: Path): List<T> = Files.list(directory).use { entries ->
        entries.filter { it.name.endsWith(JSON_SUFFIX) }.sorted(compareBy { it.name }).map { read<T>(it) }.toList()
    }
}
