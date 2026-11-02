package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.service.registry.ExternalCheckouts
import java.nio.file.Path

/**
 * Where a workspace and its index are, both as absolute normalized paths, and the directories outside it that its
 * files may be read from.
 */
internal class WorkspaceLocation(val root: Path, val indexDirectory: Path, val externalCheckouts: ExternalCheckouts)
