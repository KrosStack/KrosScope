package io.github.krosstack.kscope.cli.text.format

/**
 * How the text result says how sure a ranking is, from the margin between the best score and the ones after it.
 * [CLEAR]: the runner-up scores at most [CLEAR_SHARE] of the best. [NONE]: otherwise, [CROWD] or more files score at
 * least [CLOSE_SHARE] of the best, so nothing stands out. [CLOSE]: every other case.
 */
internal enum class MatchStrength(val wording: String) {
    CLEAR("one clear match"),
    CLOSE("several close candidates"),
    NONE("no strong match"),
    ;

    companion object {
        const val CLEAR_SHARE = 0.65
        const val CLOSE_SHARE = 0.80
        const val CROWD = 10

        fun of(scores: List<Double>): MatchStrength {
            val best = scores.firstOrNull() ?: return NONE
            val runnerUp = scores.getOrNull(1) ?: 0.0
            return when {
                runnerUp <= CLEAR_SHARE * best -> CLEAR
                scores.count { it >= CLOSE_SHARE * best } >= CROWD -> NONE
                else -> CLOSE
            }
        }
    }
}
