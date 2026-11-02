package io.github.krosstack.kscope.benchmark.agent.verification

import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

internal enum class TestOutcome { PASSED, FAILED, SKIPPED }

internal object TestResults {
    fun read(workspace: Path): Map<String, TestOutcome> {
        val outcomes = sortedMapOf<String, TestOutcome>()
        resultFiles(workspace).forEach { file ->
            cases(file).forEach { (id, outcome) -> outcomes.merge(id, outcome, ::combine) }
        }
        return outcomes
    }

    private fun resultFiles(workspace: Path): Sequence<File> = workspace.toFile().walkTopDown()
        .onEnter { it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
        .filter { file -> file.parentFile.path.replace('\\', '/').contains("/test-results/") }

    private fun cases(file: File): List<Pair<String, TestOutcome>> {
        val factory = DocumentBuilderFactory.newInstance()
        return try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val document = factory.newDocumentBuilder().parse(file)
            val nodes = document.getElementsByTagName("testcase")
            (0 until nodes.length).map { (nodes.item(it) as Element).let(::caseOf) }
        } catch (_: SAXException) {
            emptyList()
        } catch (_: IOException) {
            emptyList()
        } catch (_: ParserConfigurationException) {
            emptyList()
        }
    }

    private fun caseOf(element: Element): Pair<String, TestOutcome> {
        val id = "${element.getAttribute("classname")}#${element.getAttribute("name")}"
        val failed = element.getElementsByTagName("failure").length + element.getElementsByTagName("error").length > 0
        val skipped = element.getElementsByTagName("skipped").length > 0
        return id to when {
            failed -> TestOutcome.FAILED
            skipped -> TestOutcome.SKIPPED
            else -> TestOutcome.PASSED
        }
    }

    private fun combine(first: TestOutcome, second: TestOutcome): TestOutcome = when {
        first == TestOutcome.FAILED || second == TestOutcome.FAILED -> TestOutcome.FAILED
        first == TestOutcome.SKIPPED || second == TestOutcome.SKIPPED -> TestOutcome.SKIPPED
        else -> TestOutcome.PASSED
    }
}
