package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.AppDependsOnCoreProvider
import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The structural model whose `:app` module takes its Android namespace from the first line of its build script. */
private class NamespaceFromScriptProvider(private val delegate: BuildModelProvider = AppDependsOnCoreProvider()) :
    BuildModelProvider {
    override val producer = ProducerRef("test.namespace-from-script", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val provided = delegate.provide(request) as? BuildModelResult.Provided ?: return BuildModelResult.NotApplicable
        val script = request.artifacts.single { it.ref.path.value == APP_SCRIPT }
        val namespace = String(request.files.read(script.ref)).lineSequence().first().trim()
        val modules = provided.model.modules.map { module ->
            module.takeIf { it.path != ":app" } ?: module.copy(androidNamespace = namespace)
        }
        return provided.copy(model = provided.model.copy(modules = modules))
    }

    companion object {
        const val APP_SCRIPT = "app/build.gradle.kts"
    }
}

class ModuleFingerprintTest {
    private val appMain = "app/src/main/kotlin/com/example/app/Main.kt"
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"

    private fun registry(vararg extra: Any) = coreRegistry(*extra, providers = listOf(NamespaceFromScriptProvider()))

    private fun changeNamespace(workspace: TestWorkspace, namespace: String) {
        workspace.write(NamespaceFromScriptProvider.APP_SCRIPT, "$namespace\n")
    }

    @Test
    fun `artifact units are produced again when the definition of their module changes`() {
        val producer = RecordingProducer("by-artifact")
        scanningSample(registry(producer)) { workspace, engine ->
            changeNamespace(workspace, "one")
            engine.update()
            producer.calls.clear()

            changeNamespace(workspace, "two")
            engine.update()

            assertEquals(3, producer.producedKeys.size, "The three Kotlin files of the app")
            assertTrue(producer.producedKeys.all { it.contains(":") && "app/" in it }, "${producer.producedKeys}")
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `module units depend on their module and on the modules it depends on`() {
        val producer = RecordingProducer("by-module", granularity = OwnershipKind.MODULE)
        scanningSample(registry(producer)) { workspace, engine ->
            engine.update()
            producer.calls.clear()

            workspace.write(appMain, "fun main() = Unit\n")
            engine.update()
            assertEquals(1, producer.producedKeys.size, "Only the app changed, and nothing depends on it")
            assertTrue(producer.producedKeys.single().endsWith(":app"))
            producer.calls.clear()

            workspace.write(greeter, "class Greeter\n")
            engine.update()
            assertEquals(2, producer.producedKeys.size, "The core changed, and the app depends on it")
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `units of the wider scopes are produced again when the build model changes`() {
        listOf(OwnershipKind.BUILD_ROOT, OwnershipKind.REPOSITORY, OwnershipKind.WORKSPACE).forEach { kind ->
            val producer = RecordingProducer("by-$kind", granularity = kind)
            scanningSample(registry(producer)) { workspace, engine ->
                changeNamespace(workspace, "one")
                engine.update()
                producer.calls.clear()

                changeNamespace(workspace, "two")
                engine.update()

                assertEquals(1, producer.producedKeys.size, "units of $kind")
                engine.assertMatchesRebuild()
            }
        }
    }

    @Test
    fun `a module unit is produced again when the definition of the module changes`() {
        val producer = RecordingProducer("by-module", granularity = OwnershipKind.MODULE)
        scanningSample(registry(producer)) { workspace, engine ->
            changeNamespace(workspace, "one")
            engine.update()
            producer.calls.clear()

            changeNamespace(workspace, "two")
            engine.update()

            assertEquals(1, producer.producedKeys.size)
            assertTrue(producer.producedKeys.single().endsWith(":app"))
            engine.assertMatchesRebuild()
        }
    }
}
