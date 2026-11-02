package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn
import io.github.krosstack.kscope.store.table.FactTable

internal object BuildRootTable : FactTable<BuildRoot> {
    override val spec = TableSpec(
        name = "build_root",
        columns = listOf(
            "id TEXT NOT NULL",
            "repository TEXT NOT NULL",
            "directory TEXT NOT NULL",
            "build_system TEXT NOT NULL",
        ),
        indexes = listOf(indexOn("id")),
    )

    override fun bind(binder: Binder, value: BuildRoot) = with(binder) {
        text(value.id.value)
        text(value.repository.value)
        text(value.directory.value)
        enum(Wires.buildSystem, value.buildSystem)
    }

    override fun read(cursor: Cursor): BuildRoot = with(cursor) {
        BuildRoot(
            id = BuildRootId(text()),
            repository = RepositoryId(text()),
            directory = RepoPath(text()),
            buildSystem = enum(Wires.buildSystem),
        )
    }

    override fun wrap(value: BuildRoot): Fact = Fact.OfBuildRoot(value)

    override fun unwrap(fact: Fact): BuildRoot? = (fact as? Fact.OfBuildRoot)?.value
}
