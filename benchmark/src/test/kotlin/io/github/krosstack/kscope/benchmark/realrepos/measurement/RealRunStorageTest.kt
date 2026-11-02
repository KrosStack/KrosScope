package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.RealTimings
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealRunStorageTest {
    @TempDir
    lateinit var output: Path

    @Test
    fun `record lists deserialize one task at a time and expose later corruption on access`() {
        val task = RealCommitTask(
            "sample:task",
            "sample",
            "a".repeat(40),
            "b".repeat(40),
            1,
            "Synthetic statement",
            listOf("README.md"),
            RealSplit.DEV,
        )
        val record = RealRepositoryMeasurement(
            task,
            "freeze",
            RealMeasurementStatus.FAILED,
            RealTimings(),
            failure = "snapshot: synthetic failure",
        )
        RealRunStorage(output).save(record)
        Files.writeString(output.resolve("tasks/zz-malformed.json"), "{ incomplete")

        val records = RealRepositoryRecords.read(output)
        assertEquals(2, records.size)
        assertEquals(record, records.first())
        assertFailsWith<SerializationException> { records[1] }
    }
}
