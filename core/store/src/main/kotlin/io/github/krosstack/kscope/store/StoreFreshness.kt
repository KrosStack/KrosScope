package io.github.krosstack.kscope.store

/** Ends the previous read snapshot and asks SQLite for the external commit version on this connection. */
public fun SqliteGraphStore.dataVersion(): Long = readDataVersion()
