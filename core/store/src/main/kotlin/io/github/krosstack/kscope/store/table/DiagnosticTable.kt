package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.location
import io.github.krosstack.kscope.store.codec.locationColumns
import io.github.krosstack.kscope.store.codec.locationOrNull
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec

internal object DiagnosticTable : FactTable<Diagnostic> {
    override val spec = TableSpec(
        name = "diagnostic",
        columns = listOf("severity TEXT NOT NULL", "code TEXT NOT NULL", "message TEXT NOT NULL") +
            locationColumns("location"),
    )

    override fun bind(binder: Binder, value: Diagnostic) = with(binder) {
        enum(Wires.diagnosticSeverity, value.severity)
        text(value.code.value)
        text(value.message)
        location(value.location)
    }

    override fun read(cursor: Cursor): Diagnostic = with(cursor) {
        Diagnostic(
            severity = enum(Wires.diagnosticSeverity),
            code = ReasonCode(text()),
            message = text(),
            location = locationOrNull(),
        )
    }

    override fun wrap(value: Diagnostic): Fact = Fact.OfDiagnostic(value)

    override fun unwrap(fact: Fact): Diagnostic? = (fact as? Fact.OfDiagnostic)?.value
}
