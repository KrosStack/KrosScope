package io.github.krosstack.kscope.service.settings

/**
 * The settings file of the workspace is not valid, so a scan did not start and the index was not touched. [message]
 * lists the problems, one to a line.
 */
public class InvalidSettingsException(public val fileName: String, public val issues: List<SettingsIssue>) :
    IllegalStateException(
        issues.joinToString(separator = "\n", prefix = "$fileName is not valid, so the scan did not start:\n") {
            "  ${it.describe(fileName)}"
        },
    )
