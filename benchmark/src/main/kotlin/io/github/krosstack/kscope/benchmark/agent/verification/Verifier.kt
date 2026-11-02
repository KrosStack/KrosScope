package io.github.krosstack.kscope.benchmark.agent.verification

import io.github.krosstack.kscope.benchmark.agent.AgentTask
import io.github.krosstack.kscope.benchmark.agent.VerificationRecord
import io.github.krosstack.kscope.benchmark.agent.workspace.Workspace
import java.nio.file.Path
import java.time.Duration

internal interface Verifier {
    fun verify(task: AgentTask, workspace: Workspace, logDirectory: Path, limit: Duration? = null): VerificationRecord
}
