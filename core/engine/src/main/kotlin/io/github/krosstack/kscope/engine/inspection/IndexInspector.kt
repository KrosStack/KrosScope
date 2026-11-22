package io.github.krosstack.kscope.engine.inspection

import io.github.krosstack.kscope.store.IndexOutlineReader
import io.github.krosstack.kscope.store.IndexRead
import io.github.krosstack.kscope.store.IndexState
import java.nio.file.Path

internal object IndexInspector {
    fun inspect(databaseFile: Path, trustedProducers: () -> Set<String>): IndexInspection =
        when (val read = IndexOutlineReader.read(databaseFile)) {
            is IndexRead.Found -> IndexInspection.Readable(
                read.outline.repositories,
                IndexLevels.of(read.outline.units, trustedProducers()),
            )

            is IndexRead.Unusable -> unusable(read.state)
        }

    private fun unusable(state: IndexState): IndexInspection = when (state) {
        is IndexState.OtherFormat -> IndexInspection.OtherFormat(state.found, state.expected)
        is IndexState.Damaged -> IndexInspection.Damaged(state.reason)
        IndexState.Absent, IndexState.Readable -> IndexInspection.NoIndex
    }
}
