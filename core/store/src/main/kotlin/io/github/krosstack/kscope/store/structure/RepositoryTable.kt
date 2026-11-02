package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn
import io.github.krosstack.kscope.store.table.FactTable

internal object RepositoryTable : FactTable<Repository> {
    override val spec = TableSpec(
        name = "repository",
        columns = listOf(
            "id TEXT NOT NULL",
            "name TEXT NOT NULL",
            "head_revision TEXT",
            "origin_url TEXT",
            "directory TEXT NOT NULL",
        ),
        indexes = listOf(indexOn("id")),
    )

    override fun bind(binder: Binder, value: Repository) = with(binder) {
        text(value.id.value)
        text(value.name)
        textOrNull(value.headRevision)
        textOrNull(value.originUrl)
        text(value.directory.value)
    }

    override fun read(cursor: Cursor): Repository = with(cursor) {
        Repository(
            id = RepositoryId(text()),
            name = text(),
            headRevision = textOrNull(),
            originUrl = textOrNull(),
            directory = RepoPath(text()),
        )
    }

    override fun wrap(value: Repository): Fact = Fact.OfRepository(value)

    override fun unwrap(fact: Fact): Repository? = (fact as? Fact.OfRepository)?.value
}
