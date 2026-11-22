package io.github.krosstack.kscope.engine.inspection

import io.github.krosstack.kscope.engine.ScanLevel
import io.github.krosstack.kscope.engine.pipeline.LevelUnit
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.store.UnitKind
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexLevelsTest {
    private val trusted = setOf("build.tooling")
    private val ordinary = UnitKind(ProducerRef("build.static", "1"), OwnershipKind.REPOSITORY)
    private val tooling = UnitKind(ProducerRef("build.tooling", "1"), OwnershipKind.REPOSITORY)
    private val level = UnitKind(LevelUnit.producer, OwnershipKind.WORKSPACE)

    @Test
    fun `an index with no mark is resolved`() {
        assertEquals(ScanLevel.RESOLVED, IndexLevels.of(emptyList(), trusted))
        assertEquals(ScanLevel.RESOLVED, IndexLevels.of(listOf(ordinary), trusted))
    }

    @Test
    fun `the unit of the level producer makes an index syntax`() {
        assertEquals(ScanLevel.SYNTAX, IndexLevels.of(listOf(ordinary, level), trusted))
    }

    @Test
    fun `a repository model of a producer that needs trust makes an index exact before anything else`() {
        assertEquals(ScanLevel.EXACT, IndexLevels.of(listOf(tooling), trusted))
        assertEquals(ScanLevel.EXACT, IndexLevels.of(listOf(level, tooling), trusted))
    }

    @Test
    fun `a producer that needs trust counts only for the units of a repository`() {
        val elsewhere = UnitKind(tooling.producer, OwnershipKind.ARTIFACT)

        assertEquals(ScanLevel.RESOLVED, IndexLevels.of(listOf(elsewhere), trusted))
    }

    @Test
    fun `the level producer counts only with its own version`() {
        val other = UnitKind(ProducerRef(LevelUnit.producer.id, "0"), OwnershipKind.WORKSPACE)

        assertEquals(ScanLevel.RESOLVED, IndexLevels.of(listOf(other), trusted))
    }
}
