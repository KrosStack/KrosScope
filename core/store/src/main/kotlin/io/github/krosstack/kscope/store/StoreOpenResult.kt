package io.github.krosstack.kscope.store

public sealed interface StoreOpenResult {
    public class Opened(public val store: SqliteGraphStore) : StoreOpenResult

    /** The file holds another schema version. The index is derived data, so the caller deletes it and rebuilds. */
    public data class Incompatible(public val foundVersion: Int, public val supportedVersion: Int) : StoreOpenResult
}
