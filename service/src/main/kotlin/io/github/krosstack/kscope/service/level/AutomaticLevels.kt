package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel

/**
 * The level of a scan that nobody typed: the first scan of a workspace is quick and reads declarations only, and the
 * next one deepens to what the settings ask for. That is at most `resolved`, unless the user stored trust for the
 * workspace on this machine, which also lets it reach `exact`. It never goes shallower than the index, and an `exact`
 * index of a workspace that is no longer trusted gets a level that the scan refuses, so that the build never runs.
 */
internal object AutomaticLevels {
    fun next(indexed: ScanLevel?, fromFile: ScanLevel?, trusted: Boolean): ScanLevel = when {
        indexed == null -> ScanLevel.SYNTAX
        indexed == ScanLevel.EXACT && !trusted -> ScanLevel.RESOLVED
        else -> maxOf(indexed, target(fromFile, trusted))
    }

    fun isShallow(indexed: ScanLevel?, fromFile: ScanLevel?, trusted: Boolean): Boolean =
        indexed != null && indexed < target(fromFile, trusted)

    fun blocksUpdates(indexedExact: Boolean, trusted: Boolean): Boolean = indexedExact && !trusted

    private fun target(fromFile: ScanLevel?, trusted: Boolean): ScanLevel {
        val asked = fromFile ?: ScanLevel.RESOLVED
        return if (trusted) asked else minOf(asked, ScanLevel.RESOLVED)
    }
}
