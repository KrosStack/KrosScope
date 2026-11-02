package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolFlag

/** What the tests of the test subject linker share: a module with a main and a test source set, and the linker. */
internal open class TestSubjectBase {
    protected val draft = GraphDraft()
    protected val main = draft.sourceSet(":app", "main")
    protected val test = draft.sourceSet(":app", "test", SetShape(SourceSetRole.UNIT_TEST))
    protected val testFlag = setOf(SymbolFlag.TEST)
    private val linker = TestSubjectLinker()

    protected fun edges() = linker.linkedFrom(draft)

    protected fun testClass(name: String) = draft.type(test, name, testFlag)

    protected fun subjectOf(testClass: Declared): Relationship? = edges().singleOrNull { it.from == testClass.node }

    protected fun targetOf(testClass: Declared) = (subjectOf(testClass)?.resolution as? Resolution.Resolved)?.target
}
