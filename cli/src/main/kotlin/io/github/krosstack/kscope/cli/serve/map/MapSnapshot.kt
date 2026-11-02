package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.serve.IndexStamp
import io.github.krosstack.kscope.service.map.WorkspaceMap

/** A map that holds the facts of one index, as it stood at [stamp]. [overviewJson] is its overview, encoded. */
internal class MapSnapshot(val version: Int, val map: WorkspaceMap, val overviewJson: ByteArray, val stamp: IndexStamp)
