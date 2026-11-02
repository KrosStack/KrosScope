package io.github.krosstack.kscope.benchmark.score

private const val GENERATED_ARTIFACT = "generated-artifact"
private const val CONVENTION = "convention"

/**
 * Whether the evidence class a graph states satisfies the one gold expects. A hop from a source of truth to a
 * surface that a generator makes is expected as `generated-artifact`, the output of a real generator run. The
 * producers derive it from the documented naming rules, which is `convention`, and that counts as the same hop. Every
 * other pair of classes must be equal.
 */
internal object EvidencePolicy {
    fun satisfies(expected: String, produced: String): Boolean =
        expected == produced || (expected == GENERATED_ARTIFACT && produced == CONVENTION)
}

/**
 * How the evidence of the matched pairs compares with gold: [mismatches] are the pairs that the policy does not
 * accept, and [accepted] counts by produced class the pairs it accepts although the classes differ, so that a report
 * can say which of the two was produced.
 */
internal class EvidenceOutcome(val mismatches: Int, val accepted: Map<String, Int>)

internal fun Matching.evidenceOutcome(): EvidenceOutcome {
    val differing = pairs.filter { (expected, produced) -> expected.group != produced.group }
    val (accepted, rejected) = differing.partition { (expected, produced) ->
        EvidencePolicy.satisfies(expected.group, produced.group)
    }
    return EvidenceOutcome(rejected.size, accepted.groupingBy { it.second.group }.eachCount().toSortedMap())
}
