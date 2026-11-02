package io.github.krosstack.kscope.service

/** Forgets cached index data when SQLite reports that another connection committed changes. */
public fun KrosScope.refreshIndex() {
    if (memory.refresh()) tokenCounts?.reload()
}

/** A number that grows whenever this instance forgets what it read of the index, which includes every scan it runs. */
public fun KrosScope.indexRevision(): Long = memory.revision
