package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel

/** The level the index was built at, read once for as long as the index stays as it is. */
internal class LevelMemory(private val read: () -> ScanLevel?) {
    private var known = false
    private var level: ScanLevel? = null

    fun level(): ScanLevel? {
        if (!known) {
            level = read()
            known = true
        }
        return level
    }

    fun forget() {
        known = false
    }
}
