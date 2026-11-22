package io.github.krosstack.kscope.analyzer.kotlin.worker

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.worker.WorkerModule
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Module `a` names a Java class, a Kotlin class and packages that only module `b` declares or lists in its classpath,
 * and calls on what a method of the Java class returns. Unless `a` depends on `b`, none of it resolves, and the facts
 * of `a` are the same whether or not `b` shares the analysis session with `a`.
 */
class HiddenModulesTest {
    private val library = Path.of(Caffeine::class.java.protectionDomain.codeSource.location.toURI()).toString()
    private val a = SampleWorkspace.singleModule("a", "hidden/a", Platform.JVM)
    private val b = SampleWorkspace.singleModule("b", "hidden/b", Platform.JVM).copy(classpath = listOf(library))

    private fun batchOfA(modules: List<WorkerModule>): FactBatch =
        FactPass(WorkerIdentity.producer).batchSequence(modules).toList().single { it.owner.key.endsWith(":a@main") }

    private fun resolvedInB(batch: FactBatch): Set<String> = listOf(batch).relationships()
        .mapNotNull { ((it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id?.value }
        .filter { " shared/" in it }
        .map { it.substringAfter(" main ") }
        .toSet()

    private fun unresolvedNames(batch: FactBatch): Set<String> =
        listOf(batch).relationships().mapNotNull { (it.resolution as? Resolution.Unresolved)?.hint }.toSet()

    @Test
    fun `a module does not resolve a Java or Kotlin class of a module it does not depend on`() {
        assertEquals(emptySet(), resolvedInB(batchOfA(listOf(a, b))))
        assertEquals(emptySet(), resolvedInB(batchOfA(listOf(b, a))))
    }

    @Test
    fun `a module has the same facts with and without an unrelated module in its session`() {
        val alone = CanonicalFacts.toJson(listOf(batchOfA(listOf(a))))
        assertEquals(alone, CanonicalFacts.toJson(listOf(batchOfA(listOf(a, b)))))
        assertEquals(alone, CanonicalFacts.toJson(listOf(batchOfA(listOf(b, a)))))
    }

    @Test
    fun `a call on what a hidden class returns does not resolve either`() {
        assertTrue("length" in unresolvedNames(batchOfA(listOf(a, b))))
    }

    @Test
    fun `a package that only the classpath of another module has does not exist`() {
        assertTrue(PACKAGE in unresolvedNames(batchOfA(listOf(a, b))))
        assertFalse(PACKAGE in unresolvedNames(batchOfA(listOf(a.copy(classpath = listOf(library))))))
    }

    @Test
    fun `a wildcard import of a package that only another module has stays unresolved`() {
        assertTrue("shared" in unresolvedNames(batchOfA(listOf(a, b))))
        assertFalse("shared" in unresolvedNames(batchOfA(listOf(a.copy(dependencies = listOf("b")), b))))
    }

    @Test
    fun `a package that only a Kotlin file of another module declares does not exist`() {
        assertTrue("nowhere.pkg" in unresolvedNames(batchOfA(listOf(a, b))))
    }

    @Test
    fun `a module resolves the classes of a module it depends on`() {
        val batch = batchOfA(listOf(a.copy(dependencies = listOf("b")), b))
        val expected = setOf(
            "shared/Context#",
            "shared/Context#FLAG.",
            "shared/Context#count().",
            "shared/Context#name().",
            "shared/Counter#",
            "shared/Counter#total.",
        )
        assertEquals(expected, resolvedInB(batch))
        assertFalse("length" in unresolvedNames(batch), "the call on a returned String does not resolve")
    }

    private companion object {
        const val PACKAGE = "com.github.benmanes"
    }
}
