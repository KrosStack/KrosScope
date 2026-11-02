package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel

/** What a scan does about its level: run at [Run.level], with an optional [Run.notice], or not start. */
internal sealed interface LevelDecision {
    data class Run(val level: ScanLevel, val notice: String? = null) : LevelDecision

    data class Refuse(val message: String) : LevelDecision
}
