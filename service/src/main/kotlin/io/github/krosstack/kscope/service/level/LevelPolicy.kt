package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel

/**
 * Decides the level of a scan. A level given on the command line is the user's own request and is followed, but never
 * to a shallower level than the index has unless the index is rebuilt. Without one, the scan keeps the level of the
 * index and deepens to what the settings file asks for, and `exact` runs the build of the workspace only when the user
 * trusted the workspace on this machine ([trusted]); a settings file can ask for it, never grant it.
 */
internal class LevelPolicy(private val trusted: Boolean) {
    fun decide(explicit: ScanLevel?, fromFile: ScanLevel?, indexed: ScanLevel?, rebuild: Boolean): LevelDecision =
        if (explicit != null) followExplicit(explicit, indexed, rebuild) else keepOrDeepen(fromFile, indexed)

    private fun followExplicit(explicit: ScanLevel, indexed: ScanLevel?, rebuild: Boolean): LevelDecision =
        if (indexed != null && explicit < indexed && !rebuild) {
            LevelDecision.Refuse(shallowerWithoutRebuild(explicit, indexed))
        } else {
            LevelDecision.Run(explicit)
        }

    private fun keepOrDeepen(fromFile: ScanLevel?, indexed: ScanLevel?): LevelDecision {
        val asked = fromFile ?: ScanLevel.RESOLVED
        val refused = asked == ScanLevel.EXACT && !trusted
        val granted = if (refused) ScanLevel.RESOLVED else asked
        val level = maxOf(granted, indexed ?: granted)
        return when {
            level == ScanLevel.EXACT && !trusted -> LevelDecision.Refuse(untrustedExactIndex())
            refused -> LevelDecision.Run(level, EXACT_NEEDS_TRUST)
            else -> LevelDecision.Run(level)
        }
    }

    private fun shallowerWithoutRebuild(explicit: ScanLevel, indexed: ScanLevel) =
        "The index was built at level ${indexed.wireName}. A scan at the shallower level ${explicit.wireName} " +
            "replaces it, so it needs a rebuild: kscope scan --level ${explicit.wireName} --rebuild. " +
            "To keep the ${indexed.wireName} model, run kscope scan --level ${indexed.wireName}."

    private fun untrustedExactIndex() =
        "The index was built at level exact, which runs the build of the workspace, and a scan keeps the level of " +
            "the index. This workspace is not trusted on this machine, so the scan did not run its build. " +
            "Run `kscope scan --level exact` to run it this once, `kscope trust` to trust the workspace, or " +
            "`kscope scan --level resolved --rebuild` to give up the exact model."

    companion object {
        const val NOTICE_CODE = "level.exact-needs-trust"

        const val EXACT_NEEDS_TRUST = "kscope.toml asks for level exact, which runs the build of the workspace. " +
            "This workspace is not trusted on this machine, so the scan ran at level resolved. " +
            "Run `kscope scan --level exact` to run it once, or `kscope trust` to trust the workspace."
    }
}
