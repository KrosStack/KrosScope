package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Scans two JVM modules and looks things up through the lexical index that the scan filled. */
class SearchIntegrationTest {
    private val sample = SampleWorkspace("tests")
    private val greeter = "core/src/main/kotlin/sample/tests/Greeter.kt"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun KrosScope.search(scope: SearchScope, vararg words: String): List<SearchHit> =
        searchIndex().search(SearchQuery(words.map { SearchTerm(it) }, scope))

    private fun KrosScope.qualifiedNames(hits: List<SearchHit>): List<String?> =
        hits.map { graph().symbol((it.node as NodeRef.OfSymbol).id)?.qualifiedName }

    private fun List<SearchHit>.paths(): List<String> = map { (it.node as NodeRef.OfArtifact).ref.path.value }

    @Test
    fun `a class is found by a word of its name`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val hits = service.search(SearchScope.SYMBOLS, "greeter")
            assertEquals("sample.tests.Greeter", service.qualifiedNames(hits).first())
            assertContains(service.qualifiedNames(hits), "sample.tests.GreeterTest")
            assertContains(hits.first().fieldScores, "name")
        }
    }

    @Test
    fun `a class is found by a part of its name and a stemmed form of it`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val specs = service.search(SearchScope.SYMBOLS, "specs")
            assertContains(service.qualifiedNames(specs), "sample.tests.FormatterSpec")
            val prefix = SearchQuery(listOf(SearchTerm("greet", prefix = true)), SearchScope.SYMBOLS)
            assertContains(service.qualifiedNames(service.searchIndex().search(prefix)), "sample.tests.Greeter")
        }
    }

    @Test
    fun `a file is found by a word that only its content holds`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val hits = service.search(SearchScope.ARTIFACTS, "uppercase")
            assertEquals(listOf(greeter), hits.paths())
            assertContains(hits.single().fieldScores, "content")
        }
    }

    @Test
    fun `a documentation file is found by the words of its text`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            assertEquals(listOf("README.md"), service.search(SearchScope.ARTIFACTS, "refers", "alike").paths())
        }
    }

    @Test
    fun `info says that the search index is populated once a scan has filled it`() {
        KrosScope.open(sample.root).use { service ->
            assertFalse(service.info().searchIndexPopulated)
            service.scan()
            assertTrue(service.info().searchIndexPopulated)
        }
    }

    @Test
    fun `an edit is searchable after the next scan and the index equals a rebuild`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            sample.write(greeter, "package sample.tests\n\nclass Greeter {\n    fun whisper(): String = \"psst\"\n}\n")
            val update = service.scan(verify = true)
            assertEquals(true, update.verification?.matches, "${update.verification}")
            assertEquals(0, update.verification?.differingSearchDocuments)
            assertEquals(listOf(greeter), service.search(SearchScope.ARTIFACTS, "whisper").paths())
            assertEquals(emptyList(), service.search(SearchScope.ARTIFACTS, "uppercase").paths())
            assertNotNull(service.search(SearchScope.SYMBOLS, "whisper").firstOrNull())
        }
    }
}
