package rel.js

data class Vote(val sessionId: String, val score: Int)

class Totals(private val votes: List<Vote>) {
    val bySession: Map<String, List<Vote>> get() = votes.groupBy { it.sessionId }
    val firstBySession: Map<String, Vote> get() = votes.associateBy { it.sessionId }
    val sum: Int get() = votes.sumOf { it.score }
}

fun describe(totals: Totals): String =
    totals.bySession.entries.joinToString { (id, group) -> "$id=${group.size}/${totals.sum}" }

fun tally(votes: List<Vote>): Map<String, Int> {
    val counts = HashMap<String, Int>()
    val seen = ArrayList<Vote>()
    for (vote in votes) {
        counts[vote.sessionId] = (counts[vote.sessionId] ?: 0) + vote.score
        seen.add(vote)
    }
    return counts
}
