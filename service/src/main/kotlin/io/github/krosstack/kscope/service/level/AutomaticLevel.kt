package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.indexRequiresTrust

/**
 * The level for a scan that the watcher or the service starts by itself. The first scan of a workspace is at
 * [ScanLevel.SYNTAX]; later ones deepen to `resolved`, or to the level that `kscope.toml` asks for if that is
 * shallower. `exact` is reached only in a workspace that the user trusted on this machine, and the trust is read again
 * at every call. A scan that goes to `exact` by itself must not ask for that level in its `OpenOptions`, which is the
 * user's own request and runs the build whatever the trust is. It asks for no level, so that the scan reads the trust
 * again when it starts, and a revoked trust never runs the build.
 */
public fun KrosScope.automaticScanLevel(): ScanLevel =
    AutomaticLevels.next(indexLevel(), levelAskedBySettings, workspaceTrusted)

/** Whether the index is shallower than an automatic scan would make it, so that a scan is worth running. */
public fun KrosScope.indexIsShallow(): Boolean =
    AutomaticLevels.isShallow(indexLevel(), levelAskedBySettings, workspaceTrusted)

/** Whether an automatic update must leave the index alone: it is `exact` and the workspace is not trusted. */
public fun KrosScope.automaticUpdatesBlocked(): Boolean =
    AutomaticLevels.blocksUpdates(indexRequiresTrust(), workspaceTrusted)
