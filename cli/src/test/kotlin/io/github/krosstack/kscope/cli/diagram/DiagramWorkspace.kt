package io.github.krosstack.kscope.cli.diagram

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * A scanned workspace of the modules `:app` and `:lib`, where `:app` uses `:lib`. The simple name `Lib` names two
 * classes, `Greeter` and `salute` name one each, and `total` names a property, which a diagram does not draw.
 */
internal fun <T> inDiagramWorkspace(block: (Path) -> T): T = inWorkspace { root ->
    writeFile(root, "settings.gradle.kts", "include(\":app\", \":lib\")\n")
    writeFile(root, "app/build.gradle.kts", "dependencies {\n    implementation(project(\":lib\"))\n}\n")
    writeFile(
        root,
        "lib/src/main/kotlin/Greeter.kt",
        "package hello\n\nclass Greeter {\n    fun salute(name: String): String = \"Hello, \$name\"\n}\n\n" +
            "class Counter {\n    val total: Int = 0\n}\n",
    )
    writeFile(root, "lib/src/main/kotlin/Another.kt", "package other\n\nclass Lib\n")
    writeFile(
        root,
        "app/src/main/kotlin/Main.kt",
        "package app\n\nimport hello.Greeter\n\nclass Main {\n    private val greeter = Greeter()\n\n" +
            "    fun run(): String = greeter.salute(\"world\")\n}\n",
    )
    check(runKscope("scan", "--workspace", root.toString()).statusCode == 0) { "the workspace did not scan" }
    block(root)
}

internal fun diagramArguments(root: Path, arguments: List<String>): List<String> =
    listOf("diagram") + arguments + listOf("--workspace", root.toString(), "--quiet")

internal fun runDiagram(root: Path, arguments: List<String>): CliktCommandTestResult =
    runKscopeWith(diagramArguments(root, arguments))

internal fun runDiagram(root: Path, vararg arguments: String): CliktCommandTestResult =
    runDiagram(root, arguments.toList())

internal fun diagramNodes(diagram: JsonObject): List<JsonObject> =
    diagram.getValue("nodes").jsonArray.map { it.jsonObject }

internal fun selectedNode(diagram: JsonObject): JsonObject =
    diagramNodes(diagram).single { it["selected"]?.jsonPrimitive?.boolean == true }

internal fun labelOf(node: JsonObject): String = node.getValue("label").jsonPrimitive.content
