package io.github.krosstack.kscope.benchmark.context.semantic

import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkIdentity
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.store.GenerationOpening
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.service.components.ComponentState
import io.github.krosstack.kscope.service.components.listing
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

internal object ContextPipelineIdentityFactory {
    fun recipe(): ContentHash = snapshot().fingerprint()

    fun read(scope: KrosScope, indexDirectory: Path): ContextPipelineIdentity {
        val recipe = snapshot()
        val status = scope.analyze(
            "context benchmark identity",
            includeContextEstimates = false,
            ml = MlMode.ON,
        ).semantic
        check(status.state == SemanticState.USED && status.behindChunks == 0) {
            "Semantic ON requires a complete generation actually USED by analysis; got ${status.state}."
        }
        val provider = checkNotNull(status.provider?.takeIf { it.isNotBlank() }) {
            "Semantic ON did not report its provider identity."
        }
        val model = checkNotNull(status.model) { "Semantic ON did not report its model identity." }
        val store = GenerationStore(indexDirectory)
        val opening = store.tryOpen(provider)
        check(opening is GenerationOpening.Opened) { "Semantic ON requires a readable selected generation: $opening." }
        val generation = opening.lease.use { lease ->
            check(lease.generation.header.provider == status.provider && lease.generation.header.identity == model) {
                "Semantic ON generation does not match the analysis provider and model."
            }
            ContentHash(opening.name.substringBeforeLast('.').substringAfterLast('-'))
        }
        check(store.current(provider) == opening.name && snapshot() == recipe) {
            "Semantic ON pipeline changed while reading its identity."
        }
        return ContextPipelineIdentity(
            provider,
            model,
            recipe.implementation,
            recipe.workerFiles,
            recipe.modelFiles,
            recipe.runtime,
            recipe.policy,
            generation,
        )
    }

    private fun snapshot(): Recipe {
        val directory = ComponentDirectory.resolve(System::getProperty, System::getenv)
        val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
        val components = ComponentManifest.shipped().listing(directory, platform, WorkspaceSettings.DEFAULTS)
            .filter { it.entry.name in listOf("semantic", "semantic-model") }
        check(components.size == 2 && components.all { it.state == ComponentState.INSTALLED }) {
            "Semantic ON requires installed, enabled semantic and semantic-model components: " +
                components.joinToString { "${it.entry.name}: ${it.state.text}" }
        }
        val installed = components.associate { it.entry.name to checkNotNull(it.installed).directory }
        val worker = inventory(installed.getValue("semantic"))
        val model = inventory(installed.getValue("semantic-model"))
        check(worker.keys.any { it.startsWith("lib/") }) { "Semantic ON worker payload is missing." }
        check(
            listOf(
                "model.json",
                "model.onnx",
                "tokenizer.json",
            ).all { name -> model.keys.any { it == name || it == "model/$name" } },
        ) {
            "Semantic ON model, tokenizer or descriptor is missing."
        }
        return Recipe(implementation(), worker, model, runtime(platform))
    }

    private fun implementation(): ContentHash {
        val classes = listOf(
            ContextBenchmark::class.java,
            KrosScope::class.java,
            ContextCompiler::class.java,
            TaskRetrieval::class.java,
            ChunkBuilder::class.java,
            ModelIdentity::class.java,
            ContentHash::class.java,
        )
        val loaders = generateSequence(ContextBenchmark::class.java.classLoader) { it.parent }.toList().asReversed()
        val urls = loaders.filterIsInstance<URLClassLoader>().flatMap { it.urLs.toList() }
        val classPath = System.getProperty("java.class.path").split(java.io.File.pathSeparator)
            .filter(String::isNotBlank)
        val paths = classPath.map(Path::of) +
            (urls + classes.map { it.protectionDomain.codeSource.location }).filter { it.protocol == "file" }
                .map { Path.of(it.toURI()) }
        val hashes = paths.map { it.toAbsolutePath().normalize() }.distinct().filter(Files::exists).map { path ->
            if (Files.isDirectory(path)) digestValue(inventory(path)) else digest(path)
        }
        return digestValue(hashes)
    }

    private fun runtime(platform: String): Map<String, String> = sortedMapOf(
        "platform" to platform,
        "os-version" to System.getProperty("os.version"),
        "jvm" to System.getProperty("java.runtime.version"),
        "jdk-files" to digestValue(inventory(Path.of(System.getProperty("java.home")), links = true)).value,
        "device" to System.getProperty("kscope.semantic.device", "cpu"),
        "environment" to digestValue(
            listOf("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "LD_LIBRARY_PATH", "PATH")
                .associateWith { System.getenv(it).orEmpty() },
        ).value,
    )

    private fun inventory(root: Path, links: Boolean = false): Map<String, ContentHash> =
        Files.walk(root).use { paths ->
            paths.sorted().toList().mapNotNull { path ->
                val name = root.relativize(path).toString().replace('\\', '/')
                when {
                    Files.isSymbolicLink(path) -> {
                        check(links) { "Semantic ON identity cannot follow symbolic links." }
                        name to digestValue("link:" + Files.readSymbolicLink(path).toString().replace('\\', '/'))
                    }

                    Files.isRegularFile(path, NOFOLLOW_LINKS) -> name to digest(path)

                    else -> null
                }
            }.toMap()
        }

    private fun digest(path: Path): ContentHash {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { digest.update(buffer, 0, it) }
        }
        return ContentHash(digest.digest().toHexString())
    }

    private inline fun <reified T> digestValue(value: T): ContentHash =
        ContextBenchmarkIdentity.fingerprint(KscopeJson.encodeToJsonElement(value))

    @Serializable
    private data class Recipe(
        val implementation: ContentHash,
        val workerFiles: Map<String, ContentHash>,
        val modelFiles: Map<String, ContentHash>,
        val runtime: Map<String, String>,
        val policy: Map<String, String> = sortedMapOf(
            "protocol" to "1", "ranking" to "rrf60-equal-maxfile-top10", "chunk-builder" to "1",
            "context-input" to "fused-head-structural-tail-1",
            "source-byte-cap" to "32768", "token-cap" to "512", "ort" to "1.31.1",
            "threads" to "2,1", "batch" to "16,1", "query-settings" to "installed-model.json",
        ),
    ) {
        fun fingerprint(): ContentHash = digestValue(this)
    }
}
