package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.WorkerFile

internal fun Artifact.toWorkerFile(files: WorkspaceFiles): WorkerFile =
    WorkerFile(ref, files.absolutePath(ref).toString(), contentHash)
