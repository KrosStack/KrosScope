package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TestSubjectLinkerTest : TestSubjectBase() {
    @Test
    fun `a test is bound by its name to the class in the main source set as probable by convention`() {
        val subject = draft.type(main, "p.app.Greeter")
        val greeterTest = testClass("p.app.GreeterTest")

        val edge = checkNotNull(subjectOf(greeterTest))

        assertEquals(RelationKind.TESTS, edge.kind)
        assertEquals(Resolution.Resolved(subject.node, Certainty.PROBABLE), edge.resolution)
        assertEquals(EvidenceClass.CONVENTION, edge.evidenceClass)
        assertNull(edge.site, "A convention is expressed nowhere in the code")
        assertEquals(greeterTest.symbol.scope, edge.scope)
        assertEquals("name", edge.attributes["tests.subject-basis"])
    }

    @Test
    fun `a test that the compiler also shows using the class is exact with compiler evidence`() {
        val subject = draft.type(main, "p.app.Greeter")
        val constructor = draft.function(main, "p.app.Greeter.greet", container = subject)
        val greeterTest = testClass("p.app.GreeterTest")
        val method = draft.function(test, "p.app.GreeterTest.greets", testFlag, greeterTest)
        draft.relate(method, RelationKind.CALLS, constructor)

        val edge = checkNotNull(subjectOf(greeterTest))

        assertEquals(Resolution.Resolved(subject.node, Certainty.EXACT), edge.resolution)
        assertEquals(EvidenceClass.COMPILER, edge.evidenceClass)
    }

    @Test
    fun `the other suffixes lead to the class as well`() {
        val greeter = draft.type(main, "p.app.Greeter")
        val names = listOf("GreeterTests", "GreeterSpec", "GreeterIT", "GreeterKtTest")

        names.forEach { name ->
            assertEquals(greeter.node, targetOf(testClass("p.app.$name")), name)
        }
    }

    @Test
    fun `of several classes of the name the one in the package of the test wins`() {
        draft.type(main, "p.other.Greeter")
        val sibling = draft.type(main, "p.app.Greeter")

        assertEquals(sibling.node, targetOf(testClass("p.app.GreeterTest")))
    }

    @Test
    fun `without a class in the package the nearest module wins`() {
        val lib = draft.sourceSet(":lib", "main")
        draft.dependency(":app", ":lib")
        draft.type(lib, "p.lib.Greeter")
        val own = draft.type(main, "p.app.Greeter")

        assertEquals(own.node, targetOf(testClass("p.other.GreeterTest")))
    }

    @Test
    fun `a class of a module the test depends on is a subject, one of any other module is not`() {
        val lib = draft.sourceSet(":lib", "main")
        val unrelated = draft.sourceSet(":unrelated", "main")
        draft.type(unrelated, "p.unrelated.Stranger")
        val library = draft.type(lib, "p.lib.Greeter")
        val withoutEdge = testClass("p.app.GreeterTest")
        val strangerTest = testClass("p.app.StrangerTest")

        assertNull(targetOf(withoutEdge))
        draft.dependency(":app", ":lib")

        assertEquals(library.node, targetOf(withoutEdge))
        assertNull(targetOf(strangerTest))
    }

    @Test
    fun `an expect class is the subject and the compiler edges to its actual class confirm it`() {
        val common = draft.sourceSet(":kmp", "commonMain", SetShape(targets = listOf("android", "desktop")))
        val onDesktop = SetShape(dependsOn = listOf(common), targets = listOf("desktop"))
        val desktop = draft.sourceSet(":kmp", "desktopMain", onDesktop)
        val desktopTest = draft.sourceSet(":kmp", "desktopTest", onDesktop.copy(role = SourceSetRole.UNIT_TEST))
        val expect = draft.type(common, "p.kmp.Platform", setOf(SymbolFlag.EXPECT))
        val actual = draft.type(desktop, "p.kmp.Platform", setOf(SymbolFlag.ACTUAL))
        val platformTest = draft.type(desktopTest, "p.kmp.PlatformTest", testFlag)
        val method = draft.function(desktopTest, "p.kmp.PlatformTest.name", testFlag, platformTest)
        draft.relate(method, RelationKind.TYPE_USES, actual)

        val edge = checkNotNull(subjectOf(platformTest))

        assertEquals(Resolution.Resolved(expect.node, Certainty.EXACT), edge.resolution)
    }

    @Test
    fun `a class of another platform is not a subject`() {
        val android = draft.sourceSet(":kmp", "androidMain", SetShape(targets = listOf("android")))
        val onDesktop = SetShape(SourceSetRole.UNIT_TEST, targets = listOf("desktop"))
        val desktopTest = draft.sourceSet(":kmp", "desktopTest", onDesktop)
        draft.type(android, "p.kmp.Platform")
        val platformTest = draft.type(desktopTest, "p.kmp.PlatformTest", testFlag)

        assertNull(targetOf(platformTest))
    }

    @Test
    fun `a test without a matching name is bound to the class its references reach most, as a heuristic`() {
        val repository = draft.type(main, "p.app.Repository")
        val other = draft.type(main, "p.app.Other")
        val find = draft.function(main, "p.app.Repository.find", container = repository)
        val save = draft.function(main, "p.app.Repository.save", container = repository)
        val describe = draft.function(main, "p.app.Other.describe", container = other)
        val storage = testClass("p.app.StorageTests")
        val method = draft.function(test, "p.app.StorageTests.roundTrip", testFlag, storage)
        listOf(find, save, describe).forEach { draft.relate(method, RelationKind.CALLS, it) }

        val edge = checkNotNull(subjectOf(storage))

        assertEquals(Resolution.Resolved(repository.node, Certainty.HEURISTIC), edge.resolution)
        assertEquals(EvidenceClass.COMPILER, edge.evidenceClass)
        assertEquals("references", edge.attributes["tests.subject-basis"])
    }

    @Test
    fun `only the outermost test classes of test source sets are bound`() {
        draft.type(main, "p.app.Greeter")
        val outer = testClass("p.app.GreeterTest")
        draft.type(test, "p.app.GreeterTest.GreeterSpec", testFlag, outer)
        val misplaced = draft.type(main, "p.app.MisplacedTest", testFlag)

        assertEquals(listOf(outer.node), edges().map { it.from })
        assertNull(subjectOf(misplaced))
    }

    @Test
    fun `the links come in a stable order`() {
        draft.type(main, "p.app.Alpha")
        draft.type(main, "p.app.Beta")
        testClass("p.app.BetaTest")
        testClass("p.app.AlphaTest")

        val sources = edges().map { it.from.toString() }

        assertEquals(sources.sorted(), sources)
        assertEquals(2, sources.size)
    }
}
