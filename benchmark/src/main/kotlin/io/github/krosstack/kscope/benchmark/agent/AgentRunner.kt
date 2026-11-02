package io.github.krosstack.kscope.benchmark.agent

import java.time.Duration

internal data class RunnerSettings(
    val arms: List<Arm>,
    val repeats: Int,
    val seed: Long,
    val perRunTimeout: Duration,
    val globalCap: Duration?,
    val keepFailed: Boolean,
)

internal class RunSummary(
    val completed: List<String>,
    val failed: List<String>,
    val reused: List<String>,
    val notStarted: List<String>,
    val mismatches: List<String>,
) {
    val clean: Boolean get() = failed.isEmpty() && notStarted.isEmpty() && mismatches.isEmpty()
}

private class RunLedger {
    val completed = mutableListOf<String>()
    val failed = mutableListOf<String>()
    val reused = mutableListOf<String>()
    val notStarted = mutableListOf<String>()
    val mismatches = mutableListOf<String>()

    fun summary() = RunSummary(completed, failed, reused, notStarted, mismatches)
}

internal class AgentRunner(
    private val settings: RunnerSettings,
    private val store: RunStore,
    private val single: SingleRun,
    private val print: (String) -> Unit,
) {
    fun run(tasks: List<AgentTask>, dryRun: Boolean): RunSummary {
        val plan = RunPlan.build(tasks, settings.arms, settings.repeats, settings.seed)
        val deadline = RunDeadline.after(settings.globalCap)
        val ledger = RunLedger()
        plan.forEachIndexed { index, planned ->
            val kept = store.load(planned.key)?.takeIf { reusable(it, planned.task) }
            when {
                kept != null -> {
                    ledger.reused += planned.key
                    if (dryRun) checkExpectation(kept, ledger)
                }

                deadline.reached -> ledger.notStarted += planned.key

                else -> {
                    print("run ${index + 1}/${plan.size} ${planned.key}")
                    execute(planned, deadline, dryRun, ledger)
                }
            }
        }
        return ledger.summary()
    }

    private fun reusable(record: RunRecord, task: AgentTask): Boolean =
        (record.status == RunStatus.COMPLETED || settings.keepFailed) && record.inputs == single.inputsOf(task)

    private fun execute(planned: PlannedRun, deadline: RunDeadline, dryRun: Boolean, ledger: RunLedger) {
        val record = single.execute(planned.task, planned.arm, planned.repeat, settings.perRunTimeout, deadline)
        store.save(record)
        print("  ${describe(record)}")
        if (record.status == RunStatus.COMPLETED) ledger.completed += planned.key else ledger.failed += planned.key
        if (dryRun) checkExpectation(record, ledger)
    }

    private fun checkExpectation(record: RunRecord, ledger: RunLedger) {
        expectation(record)?.let { ledger.mismatches += "${record.key}: $it" }
    }

    private fun describe(record: RunRecord): String = when {
        record.status == RunStatus.FAILED -> "failed: ${record.failure?.code?.label}"
        record.verification?.passed == true -> "verified: pass"
        else -> "verified: fail"
    }

    private fun expectation(record: RunRecord): String? {
        val passed = record.verification?.passed
        return when {
            record.status == RunStatus.FAILED -> "the stand-in run failed: ${record.failure?.code?.label}"
            record.agent == "reference" && passed != true -> "verification must pass after the reference patch"
            record.agent == "noop" && passed != false -> "verification must fail without a change"
            else -> null
        }
    }
}
