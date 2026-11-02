package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.VariantId
import io.github.krosstack.kscope.store.codec.json
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn
import io.github.krosstack.kscope.store.table.FactTable

internal object VariantTable : FactTable<Variant> {
    override val spec = TableSpec(
        name = "variant",
        columns = listOf(
            "id TEXT NOT NULL",
            "module TEXT NOT NULL",
            "name TEXT NOT NULL",
            "build_type TEXT",
            "flavors TEXT NOT NULL",
        ),
        indexes = listOf(indexOn("id")),
    )

    override fun bind(binder: Binder, value: Variant) = with(binder) {
        text(value.id.value)
        text(value.module.value)
        text(value.name)
        textOrNull(value.buildType)
        json(value.flavors)
    }

    override fun read(cursor: Cursor): Variant = with(cursor) {
        Variant(
            id = VariantId(text()),
            module = ModuleId(text()),
            name = text(),
            buildType = textOrNull(),
            flavors = json(),
        )
    }

    override fun wrap(value: Variant): Fact = Fact.OfVariant(value)

    override fun unwrap(fact: Fact): Variant? = (fact as? Fact.OfVariant)?.value
}
