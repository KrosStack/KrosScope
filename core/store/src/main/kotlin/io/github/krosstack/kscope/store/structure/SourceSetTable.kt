package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.json
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn
import io.github.krosstack.kscope.store.table.FactTable

internal object SourceSetTable : FactTable<SourceSet> {
    override val spec = TableSpec(
        name = "source_set",
        columns = listOf(
            "id TEXT NOT NULL",
            "module TEXT NOT NULL",
            "name TEXT NOT NULL",
            "role TEXT NOT NULL",
            "depends_on TEXT NOT NULL",
            "roots TEXT NOT NULL",
            "targets TEXT NOT NULL",
            "variants TEXT NOT NULL",
            "evidence_class TEXT NOT NULL",
            "certainty TEXT NOT NULL",
        ),
        indexes = listOf(indexOn("id")),
    )

    override fun bind(binder: Binder, value: SourceSet) = with(binder) {
        text(value.id.value)
        text(value.module.value)
        text(value.name)
        enum(Wires.sourceSetRole, value.role)
        json(value.dependsOn)
        json(value.roots)
        json(value.targets)
        json(value.variants)
        enum(Wires.evidenceClass, value.evidenceClass)
        enum(Wires.certainty, value.certainty)
    }

    override fun read(cursor: Cursor): SourceSet = with(cursor) {
        SourceSet(
            id = SourceSetId(text()),
            module = ModuleId(text()),
            name = text(),
            role = enum(Wires.sourceSetRole),
            dependsOn = json(),
            roots = json(),
            targets = json(),
            variants = json(),
            evidenceClass = enum(Wires.evidenceClass),
            certainty = enum(Wires.certainty),
        )
    }

    override fun wrap(value: SourceSet): Fact = Fact.OfSourceSet(value)

    override fun unwrap(fact: Fact): SourceSet? = (fact as? Fact.OfSourceSet)?.value
}
