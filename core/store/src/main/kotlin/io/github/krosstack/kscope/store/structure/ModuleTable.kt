package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.attributes
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.json
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn
import io.github.krosstack.kscope.store.table.FactTable

internal object ModuleTable : FactTable<Module> {
    override val spec = TableSpec(
        name = "module",
        columns = listOf(
            "id TEXT NOT NULL",
            "build_root TEXT NOT NULL",
            "path TEXT NOT NULL",
            "directory TEXT NOT NULL",
            "plugins TEXT NOT NULL",
            "traits TEXT NOT NULL",
            "android_namespace TEXT",
            "evidence_class TEXT NOT NULL",
            "certainty TEXT NOT NULL",
            "attributes TEXT NOT NULL",
        ),
        indexes = listOf(indexOn("id")),
    )

    override fun bind(binder: Binder, value: Module) = with(binder) {
        text(value.id.value)
        text(value.buildRoot.value)
        text(value.path)
        text(value.directory.value)
        json(value.plugins)
        json(value.traits.sorted())
        textOrNull(value.androidNamespace)
        enum(Wires.evidenceClass, value.evidenceClass)
        enum(Wires.certainty, value.certainty)
        attributes(value.attributes)
    }

    override fun read(cursor: Cursor): Module = with(cursor) {
        Module(
            id = ModuleId(text()),
            buildRoot = BuildRootId(text()),
            path = text(),
            directory = RepoPath(text()),
            plugins = json(),
            traits = json(),
            androidNamespace = textOrNull(),
            evidenceClass = enum(Wires.evidenceClass),
            certainty = enum(Wires.certainty),
            attributes = attributes(),
        )
    }

    override fun wrap(value: Module): Fact = Fact.OfModule(value)

    override fun unwrap(fact: Fact): Module? = (fact as? Fact.OfModule)?.value
}
