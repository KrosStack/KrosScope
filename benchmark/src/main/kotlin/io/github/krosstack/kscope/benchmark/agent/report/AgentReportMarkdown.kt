package io.github.krosstack.kscope.benchmark.agent.report

internal object AgentReportMarkdown {
    fun render(report: AggregateReport): String = buildString {
        appendLine("# Agent benchmark")
        appendLine()
        appendLine(header(report))
        appendLine()
        appendLine("## Arms")
        appendLine()
        report.arms.forEach { appendLine("- ${it.arm.letter}: ${it.arm.description}") }
        appendLine()
        appendLine("## Per arm, all tasks")
        appendLine()
        append(armTable(report))
        appendLine()
        appendLine("## Per task")
        appendLine()
        append(taskTable(report))
        append(issues("Failed runs", report.failedRuns))
        append(issues("Missing runs", report.missingRuns))
        append(issues("Runs with environment warnings", report.warningRuns))
        appendLine()
        append(NOTES)
    }

    private fun header(report: AggregateReport): String {
        val mode = if (report.dryRun) {
            "Dry run with the stand-in agent `${report.agent}`; no model was called."
        } else {
            "Agent `${report.agent}`, model `${report.model ?: "default"}`."
        }
        val context = "Context budget ${report.budget} tokens (${report.estimator})"
        return "$mode $context; prompt template ${report.promptVersion}."
    }

    private fun armTable(report: AggregateReport): String = buildString {
        appendLine(
            "| Arm | Runs | Success | Tokens | Uncached | Cost USD | Turns | Tool calls | " +
                "Context tokens | Context recall | Gold recall |",
        )
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        report.arms.forEach { arm ->
            val cell = arm.cell
            appendLine(
                "| ${arm.arm.letter} | ${ReportFormat.runs(cell)} | ${ReportFormat.success(cell)} | " +
                    "${ReportFormat.number(cell.meanTotalTokens)} | " +
                    "${ReportFormat.number(cell.meanUncachedTokens)} | " +
                    "${ReportFormat.money(cell.meanCostUsd)} | ${ReportFormat.decimal(cell.meanTurns)} | " +
                    "${ReportFormat.decimal(cell.meanToolCalls)} | ${ReportFormat.number(cell.meanContextTokens)} | " +
                    "${ReportFormat.percent(cell.meanContextRecall)} | ${ReportFormat.percent(cell.meanGoldRecall)} |",
            )
        }
        appendLine()
        appendLine(
            "| Arm | Tokens per solved task | Cost per solved task | Token ratio to A | Cost ratio to A | Cache hit |",
        )
        appendLine("|---|---|---|---|---|---|")
        report.arms.forEach { arm ->
            appendLine(
                "| ${arm.arm.letter} | ${ReportFormat.number(arm.tokensPerSolvedTask)} | " +
                    "${ReportFormat.money(arm.costPerSolvedTask)} | ${ReportFormat.ratio(arm.tokenRatioToA)} | " +
                    "${ReportFormat.ratio(arm.costRatioToA)} | ${ReportFormat.percent(arm.cell.cacheHitRatio)} |",
            )
        }
    }

    private fun taskTable(report: AggregateReport): String = buildString {
        appendLine(
            "| Task | Arm | Runs | Success | Tokens | Cost USD | Turns | Context tokens | " +
                "Context recall | Gold recall | Extra files |",
        )
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        report.tasks.forEach { task ->
            task.cells.forEachIndexed { index, cell ->
                val label = if (index == 0) "${task.taskId} ${task.title}" else ""
                appendLine(
                    "| $label | ${cell.arm.letter} | ${ReportFormat.runs(cell)} | ${ReportFormat.success(cell)} | " +
                        "${ReportFormat.number(cell.meanTotalTokens)} | ${ReportFormat.money(cell.meanCostUsd)} | " +
                        "${ReportFormat.decimal(cell.meanTurns)} | ${ReportFormat.number(cell.meanContextTokens)} | " +
                        "${ReportFormat.percent(cell.meanContextRecall)} | " +
                        "${ReportFormat.percent(cell.meanGoldRecall)} | " +
                        "${ReportFormat.decimal(cell.meanUnnecessaryFiles)} |",
                )
            }
        }
    }

    private fun issues(title: String, runs: List<RunIssue>): String = buildString {
        appendLine()
        appendLine("## $title")
        appendLine()
        if (runs.isEmpty()) {
            appendLine("None.")
        } else {
            runs.forEach { appendLine("- ${it.taskId} arm ${it.arm.letter} repeat ${it.repeat}: ${it.reason}") }
        }
    }

    private const val NOTES = "Notes: success is pass@1 over completed runs, decided by hidden fail-to-pass and " +
        "pass-to-pass tests. A failed run is an agent-level failure (authentication, timeout, non-zero exit, agent " +
        "error) and is excluded from success. Tokens count input, cache-creation, cache-read and output tokens " +
        "as the agent reports them; cost is the agent's reported cost. Ratios to A are geometric means over tasks.\n"
}
