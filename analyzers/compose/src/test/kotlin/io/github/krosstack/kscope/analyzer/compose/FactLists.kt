package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol

internal fun List<Fact>.symbols(): List<Symbol> = filterIsInstance<Fact.OfSymbol>().map { it.value }

internal fun List<Fact>.relationships(): List<Relationship> = filterIsInstance<Fact.OfRelationship>().map { it.value }
