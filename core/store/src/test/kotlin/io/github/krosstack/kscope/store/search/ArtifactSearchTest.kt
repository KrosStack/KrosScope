package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.store.SqliteGraphStore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactSearchTest {
    private val formatter = sourceArtifact("core/src/main/kotlin/tasktide/Formatter.kt")
    private val priorities = sourceArtifact("core/src/main/kotlin/tasktide/Priorities.kt")
    private val strings = sourceArtifact("app/src/main/res/values/strings.xml", category = ArtifactCategory.RESOURCE)
    private val blob = sourceArtifact("assets/priority.dat", category = ArtifactCategory.OTHER)
    private val generated = sourceArtifact("build/generated/Priority.kt").copy(generated = GeneratedInfo("ksp", null))

    private val content = FakeContent(
        texts = mapOf(
            formatter.ref.path.value to "fun formatPriority(priority: Int): String = labels[priority]",
            priorities.ref.path.value to "enum class Level { LOW, HIGH }",
            strings.ref.path.value to "<string name=\"priority_high\">Urgent</string>",
            blob.ref.path.value to "binary words that must not be indexed",
            generated.ref.path.value to "generated words that must not be indexed",
        ),
        indexedCategories = setOf(ArtifactCategory.SOURCE, ArtifactCategory.RESOURCE),
    )

    private fun indexed(): GraphStore {
        val store = SqliteGraphStore.inMemory()
        store.addArtifacts(formatter, priorities, strings, blob, generated.copy(type = formatter.type))
        store.refreshSearchIndex(content)
        return store
    }

    @Test
    fun `a file is found by a word of its content`() = indexed().use { store ->
        val hits = store.artifactHits("urgent")
        assertEquals(listOf(strings.ref.path.value), hits.artifactPaths())
        assertContains(hits.single().fieldScores, "content")
    }

    @Test
    fun `a file is found by a word of its path`() = indexed().use { store ->
        val hits = store.artifactHits("formatter")
        assertEquals(formatter.ref.path.value, hits.artifactPaths().first())
        assertContains(hits.first().fieldScores, "path_parts")
    }

    @Test
    fun `the words of a path weigh more than the words of the content`() = indexed().use { store ->
        val paths = store.artifactHits("priority").artifactPaths()
        assertTrue(paths.indexOf(priorities.ref.path.value) < paths.indexOf(formatter.ref.path.value), "$paths")
    }

    @Test
    fun `artifacts outside the indexed categories or generated are found by path only`() = indexed().use { store ->
        assertEquals(emptyList(), store.artifactHits("binary", "words").artifactPaths())
        assertContains(store.artifactHits("priority").artifactPaths(), blob.ref.path.value)
        assertContains(store.artifactHits("generated").artifactPaths(), generated.ref.path.value)
    }

    @Test
    fun `a changed file is read again and the words it lost are gone`() = indexed().use { store ->
        content.texts = content.texts + (priorities.ref.path.value to "val renamed = 1")
        store.addArtifacts(formatter, priorities.copy(contentHash = ContentHash("new")))
        store.refreshSearchIndex(content)
        assertEquals(emptyList(), store.artifactHits("high").artifactPaths())
        assertEquals(listOf(priorities.ref.path.value), store.artifactHits("renamed").artifactPaths())
    }

    @Test
    fun `a file that is deleted leaves the index`() = indexed().use { store ->
        store.addArtifacts(formatter, strings, blob)
        store.refreshSearchIndex(content)
        assertEquals(emptyList(), store.artifactHits("level").artifactPaths())
        assertTrue(priorities.ref.path.value !in store.artifactHits("priority").artifactPaths())
    }

    @Test
    fun `an unchanged inventory reads no content`() = indexed().use { store ->
        val before = content.reads
        store.refreshSearchIndex(content)
        assertEquals(before, content.reads)
    }

    @Test
    fun `a file that could not be read is tried again and found by path meanwhile`() = indexed().use { store ->
        val unreadable = sourceArtifact("core/src/Late.kt")
        store.addArtifacts(formatter, priorities, strings, blob, unreadable)
        store.refreshSearchIndex(content)
        assertContains(store.artifactHits("late").artifactPaths(), unreadable.ref.path.value)
        assertEquals(emptyList(), store.artifactHits("eventually").artifactPaths())
        content.texts = content.texts + (unreadable.ref.path.value to "val eventually = true")
        store.refreshSearchIndex(content)
        assertEquals(listOf(unreadable.ref.path.value), store.artifactHits("eventually").artifactPaths())
    }

    @Test
    fun `a change of the indexed categories brings the content of those files in or out`() = indexed().use { store ->
        content.indexedCategories = setOf(ArtifactCategory.SOURCE)
        store.refreshSearchIndex(content)
        assertEquals(emptyList(), store.artifactHits("urgent").artifactPaths())
        content.indexedCategories = setOf(ArtifactCategory.SOURCE, ArtifactCategory.RESOURCE)
        store.refreshSearchIndex(content)
        assertEquals(listOf(strings.ref.path.value), store.artifactHits("urgent").artifactPaths())
    }
}
