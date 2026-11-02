package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** A module `:bridge` with a `main` source set whose roots are the Kotlin and the native directories. */
internal object NativeModel {
    val repository = RepositoryId("repo")
    val module = ModuleId("repo//:bridge")
    val sourceSet = SourceSetId("repo//:bridge@main")
    val sourceSetUnit = OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.value)

    private val buildRoot = BuildRootId("repo//")

    val model = BuildModel(
        buildRoots = listOf(BuildRoot(buildRoot, repository, RepoPath(""), BuildSystem.GRADLE)),
        modules = listOf(
            Module(
                id = module,
                buildRoot = buildRoot,
                path = ":bridge",
                directory = RepoPath("bridge"),
                plugins = emptyList(),
                traits = setOf(ModuleTrait.ANDROID_LIBRARY),
                androidNamespace = "sample.bridge",
                evidenceClass = EvidenceClass.BUILD_MODEL,
                certainty = Certainty.EXACT,
            ),
        ),
        targets = emptyList(),
        variants = emptyList(),
        sourceSets = listOf(
            SourceSet(
                id = sourceSet,
                module = module,
                name = "main",
                role = SourceSetRole.MAIN,
                dependsOn = emptyList(),
                roots = listOf(SourceRoot(RepoPath("bridge/src/main/cpp"), SourceRootKind("cpp"), false)),
                targets = emptyList(),
                variants = emptyList(),
                evidenceClass = EvidenceClass.BUILD_MODEL,
                certainty = Certainty.EXACT,
            ),
        ),
        dependencies = emptyList(),
    )

    val structure = WorkspaceStructure(
        repositories = emptyList(),
        buildRoots = emptyList(),
        modules = model.modules,
        targets = model.targets,
        variants = emptyList(),
        sourceSets = model.sourceSets,
    )
}

private val unknownType = ArtifactType("unknown", ArtifactCategory.OTHER)
private val detectors = JniCapabilities.detectors() +
    listOf(sourceDetector("kotlin.source", "kt"), sourceDetector("java.source", "java"))

private fun sourceDetector(typeId: String, extension: String) = object : ArtifactDetector {
    private val type = ArtifactType(typeId, ArtifactCategory.SOURCE)

    override val id = typeId

    override fun detect(candidate: ArtifactCandidate): ArtifactType? =
        type.takeIf { candidate.path.value.endsWith(".$extension") }
}

/** The native files of a test as artifacts of [NativeModel], and the facts the producer makes of them. */
internal class NativeWorkspace(contents: Map<String, String>) {
    private val texts = contents.toSortedMap()
    val artifacts: List<Artifact> = texts.keys.map(::artifactOf)
    private val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = texts.getValue(ref.path.value).toByteArray()

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    fun produce(
        producer: FactProducer = NativeFunctionProducer(),
        inModule: Boolean = true,
        readFrom: WorkspaceFiles = files,
    ): List<FactBatch> {
        val units = unitsOf(producer.descriptor, inModule)
        val batches = mutableListOf<FactBatch>()
        producer.produce(ProduceRequest(units, NativeModel.model, readFrom, emptyList(), emptyMap(), artifacts)) {
            batches += it
        }
        return batches
    }

    private fun unitsOf(descriptor: ProducerDescriptor, inModule: Boolean): List<AnalysisUnit> {
        val sourceSet = NativeModel.model.sourceSets.single().takeIf { inModule }
        val module = NativeModel.model.modules.single().takeIf { inModule }
        val consumed = artifacts.filter { it.type in descriptor.consumes }
        return when {
            consumed.isEmpty() -> emptyList()

            descriptor.granularity == OwnershipKind.SOURCE_SET ->
                listOf(AnalysisUnit(NativeModel.sourceSetUnit, consumed, sourceSet, module))

            else -> consumed.map {
                AnalysisUnit(OwnershipUnit(OwnershipKind.ARTIFACT, it.ref.path.value), listOf(it), sourceSet, module)
            }
        }
    }

    fun symbols(producer: FactProducer = NativeFunctionProducer()) =
        produce(producer).flatMap { it.facts }.filterIsInstance<Fact.OfSymbol>().map { it.value }

    private fun artifactOf(path: String): Artifact {
        val bytes = texts.getValue(path).toByteArray()
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes }
        return Artifact(
            ref = ArtifactRef(NativeModel.repository, RepoPath(path)),
            type = detectors.firstNotNullOfOrNull { it.detect(candidate) } ?: unknownType,
            contentHash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
            sizeBytes = bytes.size.toLong(),
            lineCount = bytes.count { it == '\n'.code.toByte() },
            sourceSet = NativeModel.sourceSet.takeIf { path.startsWith("bridge/src/main/cpp/") },
            generated = null,
        )
    }
}

/** The reference workspace's C file, which defines two JNI functions behind a static helper. */
internal const val CHECKSUM_C = """#include <jni.h>
#include <stdint.h>

static uint32_t crc32_update(uint32_t crc, uint8_t byte) {
    crc ^= byte;
    return crc;
}

JNIEXPORT jint JNICALL
Java_sample_tasktide_nativebridge_Checksum_crc32(JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize length = (*env)->GetArrayLength(env, data);
    return (jint) crc32_update(0, 0) + length;
}

JNIEXPORT jstring JNICALL
Java_sample_tasktide_nativebridge_Checksum_nativeVersion(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "crc32-1");
}
"""

internal const val CHECKSUM_PATH = "bridge/src/main/cpp/checksum.c"
