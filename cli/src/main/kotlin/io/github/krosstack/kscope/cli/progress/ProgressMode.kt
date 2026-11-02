package io.github.krosstack.kscope.cli.progress

/** The value of `--progress`. */
internal enum class ProgressMode { AUTO, ALWAYS, NEVER }

/** How progress reaches standard error. */
internal enum class ProgressStyle {
    /** One status line that is redrawn in place. */
    LIVE,

    /** One plain line per stage, plus a heartbeat in a long stage. */
    PLAIN,

    OFF,
    ;

    companion object {
        /** `--quiet` and `never` show nothing; `always` forces the live line; `auto` follows the stream. */
        fun choose(mode: ProgressMode, quiet: Boolean, stderrIsConsole: Boolean): ProgressStyle = when {
            quiet || mode == ProgressMode.NEVER -> OFF
            mode == ProgressMode.ALWAYS -> LIVE
            stderrIsConsole -> LIVE
            else -> PLAIN
        }
    }
}
