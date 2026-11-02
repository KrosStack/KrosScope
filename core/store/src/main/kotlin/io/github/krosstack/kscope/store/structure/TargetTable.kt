package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn
import io.github.krosstack.kscope.store.table.FactTable

internal object TargetTable : FactTable<Target> {
    override val spec = TableSpec(
        name = "target",
        columns = listOf("id TEXT NOT NULL", "module TEXT NOT NULL", "name TEXT NOT NULL", "platform TEXT NOT NULL"),
        indexes = listOf(indexOn("id")),
    )

    override fun bind(binder: Binder, value: Target) = with(binder) {
        text(value.id.value)
        text(value.module.value)
        text(value.name)
        enum(Wires.platform, value.platform)
    }

    override fun read(cursor: Cursor): Target = with(cursor) {
        Target(id = TargetId(text()), module = ModuleId(text()), name = text(), platform = enum(Wires.platform))
    }

    override fun wrap(value: Target): Fact = Fact.OfTarget(value)

    override fun unwrap(fact: Fact): Target? = (fact as? Fact.OfTarget)?.value
}
