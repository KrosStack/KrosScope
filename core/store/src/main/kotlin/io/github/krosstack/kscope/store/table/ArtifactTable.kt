package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.nodeRefColumns
import io.github.krosstack.kscope.store.codec.nodeRefOrNull
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn

internal object ArtifactTable : FactTable<Artifact> {
    override val spec = TableSpec(
        name = "artifact",
        columns = listOf(
            "repository TEXT NOT NULL",
            "path TEXT NOT NULL",
            "type_id TEXT NOT NULL",
            "type_category TEXT NOT NULL",
            "content_hash TEXT NOT NULL",
            "size_bytes INTEGER NOT NULL",
            "line_count INTEGER NOT NULL",
            "source_set TEXT",
            "generated_by TEXT",
        ) + nodeRefColumns("generated_source", required = false),
        indexes = listOf(indexOn("repository", "path"), indexOn("type_id")),
    )

    override fun bind(binder: Binder, value: Artifact) = with(binder) {
        text(value.ref.repository.value)
        text(value.ref.path.value)
        text(value.type.id)
        enum(Wires.artifactCategory, value.type.category)
        text(value.contentHash.value)
        long(value.sizeBytes)
        int(value.lineCount)
        textOrNull(value.sourceSet?.value)
        textOrNull(value.generated?.generator)
        nodeRefOrNull(value.generated?.sourceOfTruth)
    }

    override fun read(cursor: Cursor): Artifact = with(cursor) {
        Artifact(
            ref = ArtifactRef(RepositoryId(text()), RepoPath(text())),
            type = ArtifactType(text(), enum(Wires.artifactCategory)),
            contentHash = ContentHash(text()),
            sizeBytes = long(),
            lineCount = int(),
            sourceSet = textOrNull()?.let(::SourceSetId),
            generated = generatedInfo(),
        )
    }

    override fun wrap(value: Artifact): Fact = Fact.OfArtifact(value)

    override fun unwrap(fact: Fact): Artifact? = (fact as? Fact.OfArtifact)?.value

    private fun Cursor.generatedInfo(): GeneratedInfo? {
        val generator = textOrNull()
        val sourceOfTruth = nodeRefOrNull()
        return generator?.let { GeneratedInfo(it, sourceOfTruth) }
    }
}
