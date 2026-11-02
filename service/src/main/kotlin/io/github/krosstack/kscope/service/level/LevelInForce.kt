package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.settings.SettingsSource
import java.sql.SQLException

/**
 * The settings of the workspace, the level that the user asked for on the command line if any, and the level of the
 * scan that runs now.
 */
internal class LevelInForce(val settings: SettingsSource, private val explicit: ScanLevel?) {
    @Volatile
    private var current: ScanLevel = explicit ?: ScanLevel.RESOLVED

    fun current(): ScanLevel = current

    fun fix(level: ScanLevel) {
        current = level
    }

    /**
     * The level of the scan that is about to start, or [LevelRefusedException] when it is not allowed. [indexed] reads
     * the level of the index. A rebuild replaces that index and is how an unreadable one is recovered, so it does not
     * read the level when it has been given one, and takes a level that cannot be read for an index with none.
     */
    fun decide(indexed: () -> ScanLevel?, rebuild: Boolean): LevelDecision.Run {
        val policy = LevelPolicy(settings.isTrusted())
        val level = when {
            !rebuild -> indexed()
            explicit != null -> null
            else -> readableOrNull(indexed)
        }
        return when (val decision = policy.decide(explicit, settings.requestedLevel(), level, rebuild)) {
            is LevelDecision.Run -> decision
            is LevelDecision.Refuse -> throw LevelRefusedException(decision.message)
        }
    }

    private fun readableOrNull(indexed: () -> ScanLevel?): ScanLevel? = try {
        indexed()
    } catch (_: SQLException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
}
