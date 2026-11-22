package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.retrieval.SymbolResult
import io.github.krosstack.kscope.service.NodeResolution

public class NarrowedSymbol(public val result: SymbolResult, public val origin: SearchOrigin)

public class NarrowedResolution(public val resolution: NodeResolution, public val origin: SearchOrigin = SearchOrigin())
