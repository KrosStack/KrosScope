package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentationTextTest {
    @Test
    fun `the first sentence of a one line comment is returned`() {
        assertEquals("Loads tasks.", firstSentence("/** Loads tasks. More text. */"))
    }

    @Test
    fun `a multi line comment is joined into one sentence`() {
        val comment = "/**\n * Loads the tasks from\n * the cache. Then more.\n */"
        assertEquals("Loads the tasks from the cache.", firstSentence(comment))
    }

    @Test
    fun `a blank line ends the description`() {
        assertEquals("No full stop here", firstSentence("/**\n * No full stop here\n *\n * Second paragraph.\n */"))
    }

    @Test
    fun `block tags are not part of the description`() {
        assertEquals("Finds it.", firstSentence("/**\n * Finds it.\n * @param id the id\n */"))
        assertNull(firstSentence("/** @return only a tag */"))
    }

    @Test
    fun `inline tags links and markup are reduced to their text`() {
        val comment = "/** <p>Uses {@code inline} and {@link Other#method} and [KdocLink] together. */"
        assertEquals("Uses inline and Other#method and KdocLink together.", firstSentence(comment))
    }

    @Test
    fun `an inline tag without content leaves no trace and brackets that are not links stay`() {
        assertNull(firstSentence("/** {@inheritDoc} */"))
        assertEquals("Reads arr[0] first.", firstSentence("/** Reads arr[0] first. */"))
    }

    @Test
    fun `a dot inside a word does not end the sentence`() {
        assertEquals("Reads version 1.5 of the file.", firstSentence("/** Reads version 1.5 of the file. */"))
    }

    @Test
    fun `an empty comment has no summary`() {
        assertNull(firstSentence("/** */"))
    }
}
