package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.context.ContextRendering
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class PresentFilesTest {
    private val random = Random(SEED)

    @Test
    fun `a value with a proposal is the left fold over the arranged files down to the last bit`() {
        repeat(ROUNDS) { _ ->
            val files = PresentFiles()
            val slots = List(SLOTS) { FileSlot(artifactNamed("file$it"), it, 0) }
            slots.filter { random.nextBoolean() }.shuffled(random).forEach { accepted(files, it) }

            for (slot in slots) {
                val rendered = renderedWithRandomValues()
                val arranged = files.arranged(slot, rendered)

                assertEquals(expectedOrder(slots, slot, rendered), arranged)
                assertEquals(foldOf(arranged).toRawBits(), files.valueWith(slot, rendered).toRawBits())
            }
            assertEquals(foldOf(files.rendered()).toRawBits(), files.totalValue().toRawBits())
        }
    }

    @Test
    fun `files are kept in rank order whatever the order in which they were added`() {
        val files = PresentFiles()
        val slots = List(SLOTS) { FileSlot(artifactNamed("file$it"), it, 0) }

        slots.shuffled(random).forEach { accepted(files, it) }

        assertEquals(slots.map { it.rendered }, files.rendered())
    }

    @Test
    fun `no present file gives a value of zero and a proposal alone gives the sum of its values`() {
        val files = PresentFiles()
        val slot = FileSlot(artifactNamed("only"), 0, 0)
        val rendered = renderedFile(0.25, 0.5)

        assertEquals(0.0.toRawBits(), files.totalValue().toRawBits())
        assertEquals(0.75.toRawBits(), files.valueWith(slot, rendered).toRawBits())
        assertEquals(listOf(rendered), files.arranged(slot, rendered))
    }

    @Test
    fun `an arrangement replaces the rendering of a present file in place`() {
        val files = PresentFiles()
        val slot = FileSlot(artifactNamed("file"), 0, 0)
        val before = accepted(files, slot)
        val replacement = renderedFile(2.0)

        assertSame(replacement, files.arranged(slot, replacement).single())
        assertSame(before, files.rendered().single())
    }

    private fun accepted(files: PresentFiles, slot: FileSlot): RenderedFile {
        val rendered = renderedWithRandomValues()
        val fragment = fragmentOf(slot.artifact, ContextRendering.OUTLINE, 1.0)
        slot.accept(fragment.node, fragment, rendered)
        files.add(slot)
        return rendered
    }

    private fun renderedWithRandomValues(): RenderedFile =
        RenderedFile(emptyList(), DoubleArray(random.nextInt(MAX_VALUES + 1)) { randomValue() }, 0L, 0)

    private fun randomValue(): Double = random.nextDouble() * TEN.pow(random.nextInt(-EXPONENT, EXPONENT))

    private fun expectedOrder(slots: List<FileSlot>, slot: FileSlot, rendered: RenderedFile): List<RenderedFile> =
        slots.filter { it.isPresent || it === slot }.map { if (it === slot) rendered else requireNotNull(it.rendered) }

    private fun foldOf(files: List<RenderedFile>): Double {
        var sum = 0.0
        for (file in files) for (value in file.values) sum += value
        return sum
    }

    private companion object {
        const val SEED = 20_261_012L
        const val ROUNDS = 200
        const val SLOTS = 7
        const val MAX_VALUES = 4
        const val EXPONENT = 9
        const val TEN = 10.0
    }
}
