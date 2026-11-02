package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode

/** Tells which patterns of a scope match none of the files that a scan could read. */
internal object PatternNotices {
    private val code = ReasonCode("discovery.pattern-matches-nothing")

    fun forListing(scope: ScanScope, paths: List<String>): List<Diagnostic> =
        unmatched("scan.include", scope.include, paths) + unmatched("scan.exclude", scope.exclude, paths)

    fun forGenerated(scope: ScanScope, paths: List<String>): List<Diagnostic> =
        unmatched("scan.generated", scope.generated, paths)

    private fun unmatched(key: String, patterns: List<String>, paths: List<String>): List<Diagnostic> =
        patterns.distinct().sorted().filter { matchesNone(it, paths) }.map {
            Diagnostic(DiagnosticSeverity.INFO, code, "$key pattern '$it' matches no file.", null)
        }

    private fun matchesNone(pattern: String, paths: List<String>): Boolean {
        val compiled = ScanPattern(pattern)
        return paths.none(compiled::matches)
    }
}
