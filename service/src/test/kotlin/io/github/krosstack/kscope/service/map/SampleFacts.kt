package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.map.tree.MapFacts

internal const val A_FILE = "core/src/main/kotlin/io/x/model/A.kt"
internal const val B_FILE = "core/src/main/kotlin/io/x/model/B.kt"
internal const val C_FILE = "core/src/main/kotlin/io/x/util/C.kt"
internal const val X_FILE = "lib/src/main/kotlin/p/q/r/X.kt"
internal const val Y_FILE = "lib/src/main/kotlin/p/Y.kt"

private val core = moduleOf(":core", "core")
private val lib = moduleOf(":lib", "lib")
private val coreMain = sourceSetOf(core, "main")
private val libMain = sourceSetOf(lib, "main")

internal fun fileId(path: String) = "f:repo//$path"

internal fun sampleMap(): WorkspaceMap = WorkspaceMapBuilder(sampleFacts()).build()

internal fun WorkspaceMap.node(id: String): MapNode = overview.nodes[position(id)]

internal fun WorkspaceMap.position(id: String): Int = checkNotNull(positionOf(id)) { id }

internal fun sampleFacts() = MapFacts(
    workspace = "demo",
    level = "resolved",
    structure = structureOf(listOf(core, lib), listOf(coreMain, libMain)),
    artifacts = listOf(
        artifactAt(A_FILE, coreMain, lines = 40),
        artifactAt(B_FILE, coreMain),
        artifactAt(C_FILE, coreMain),
        artifactAt("core/build.gradle.kts"),
        artifactAt(X_FILE, libMain),
        artifactAt(Y_FILE, libMain),
        artifactAt("README.md"),
    ),
    symbols = sampleSymbols(),
    relationships = sampleRelationships(),
    commits = mapOf(ArtifactRef(repo, RepoPath(A_FILE)) to 7),
)

private fun sampleSymbols() = listOf(
    symbolOf("io.x.model.A", A_FILE, line = 3),
    symbolOf("io.x.model.A2", A_FILE, line = 1),
    symbolOf("io.x.model.B", B_FILE),
    symbolOf("io.x.model.B2", B_FILE, line = 5),
    symbolOf("io.x.util.C", C_FILE),
    symbolOf("io.x.model.G", origin = SymbolOrigin.Generated("ksp", fileOf(B_FILE))),
)

private fun sampleRelationships(): List<Relationship> {
    val a = sym("io.x.model.A")
    return listOf(
        edge(a, sym("io.x.model.B")),
        edge(a, sym("io.x.model.B2")),
        edge(fileOf(A_FILE), fileOf(B_FILE), RelationKind.IMPORTS, Certainty.HEURISTIC),
        edge(a, NodeRef.OfExternal("maven:org.jetbrains:annotations:24.0"), RelationKind.TYPE_USES),
        edge(a, NodeRef.OfExternal("npm:left-pad"), RelationKind.REFERENCES, Certainty.PROBABLE),
        edge(a, sym("io.x.model.A2")),
        edge(fileOf(A_FILE), a, RelationKind.CONTAINS),
        edge(a, sym("ghost")),
        unresolvedFrom(a),
        notAnalysedFrom(a),
        unresolvedFrom(sym("io.x.util.C")),
    )
}
