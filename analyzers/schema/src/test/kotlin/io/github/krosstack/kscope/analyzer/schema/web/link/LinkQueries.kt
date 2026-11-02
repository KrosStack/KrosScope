package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.analyzer.schema.web.BundleWorld
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol

internal fun BundleWorld.copies(): List<Symbol> = copyFacts.symbols().filter { it.kind.id == "gradle.copy-source" }

internal fun BundleWorld.packages(): List<Symbol> = webFacts.symbols().filter { it.kind.id == "npm.package" }

internal fun BundleWorld.copyOf(link: Relationship): Symbol = copies().single { NodeRef.OfSymbol(it.id) == link.from }

internal fun Relationship.target(): NodeRef = (resolution as Resolution.Resolved).target

internal fun BundleWorld.targets(): Map<String, NodeRef> = links().associate { copyOf(it).name to it.target() }
