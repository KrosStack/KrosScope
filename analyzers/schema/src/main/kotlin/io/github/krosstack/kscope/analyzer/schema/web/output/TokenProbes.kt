package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken

internal fun List<SourceToken>.hasPunctuationAt(index: Int, symbol: String): Boolean =
    getOrNull(index)?.isPunctuation(symbol) == true

internal fun List<SourceToken>.hasIdentifierAt(index: Int, name: String): Boolean =
    getOrNull(index)?.isIdentifier(name) == true
