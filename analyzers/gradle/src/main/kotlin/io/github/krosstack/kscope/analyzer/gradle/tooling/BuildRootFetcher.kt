package io.github.krosstack.kscope.analyzer.gradle.tooling

import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import java.nio.file.Path

/**
 * Fetches every Gradle build of a workspace. The directories are visited from the shallowest down, and one that a
 * fetched build already reported, such as the included build of a convention plugin, is not fetched again. The result
 * is a JSON array that holds the array of project documents of each fetch.
 */
internal class BuildRootFetcher(private val fetcher: ModelFetcher, private val javaHome: Path? = null) {
    fun fetch(settingsDirectories: List<Path>, assets: PluginAssets): FetchOutcome {
        val fetches = mutableListOf<String>()
        val covered = mutableSetOf<Path>()
        for (directory in settingsDirectories.map(::canonical).distinct().sortedBy { it.nameCount }) {
            if (directory in covered) continue
            when (val outcome = fetcher.fetch(FetchRequest(directory, assets.jar, assets.initScript, javaHome))) {
                is FetchOutcome.Rejected -> return outcome

                is FetchOutcome.Fetched -> {
                    fetches += outcome.json
                    covered += ModelDocuments.parse(outcome.json).map { canonical(Path.of(it.build.rootDir)) }
                }
            }
        }
        return FetchOutcome.Fetched(fetches.joinToString(",", "[", "]"))
    }

    private fun canonical(path: Path): Path = path.toAbsolutePath().normalize()
}
