package io.github.krosstack.kscope.benchmark.realrepos.catalog

import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.RealWorkspaceMember
import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogManifestImporterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a task with gold in several repositories becomes a workspace task with workspace gold paths`() {
        withCatalog { catalog, freeze, checkouts, shas ->
            val manifest = CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.TEST)

            assertEquals(listOf("api", "app"), manifest.repositories.map { it.id })
            val both = manifest.tasks.single { it.id == "app:both" }
            assertEquals("app", both.repository)
            assertEquals(4, both.level)
            assertEquals(false, both.named)
            assertEquals(listOf("api/README.md", "app/README.md"), both.goldFiles)
            assertEquals(
                listOf(
                    RealWorkspaceMember("api", shas.getValue("api1"), shas.getValue("api2")),
                    RealWorkspaceMember("app", shas.getValue("app1"), shas.getValue("app2")),
                ),
                both.workspace,
            )
            assertEquals(RealSplit.TEST, both.split)
        }
    }

    @Test
    fun `a task with gold in one repository stays a plain task with repository paths`() {
        withCatalog { catalog, freeze, checkouts, shas ->
            val single = CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.DEV)
                .tasks.single { it.id == "app:one" }

            assertEquals(listOf("README.md"), single.goldFiles)
            assertEquals(emptyList(), single.workspace)
            assertEquals(shas.getValue("app1"), single.parent)
            assertEquals(1, single.level)
            assertEquals(true, single.named)
            assertEquals(RealSplit.DEV, single.split)
        }
    }

    @Test
    fun `a catalog that differs from its freeze file is rejected`() {
        withCatalog { catalog, freeze, checkouts, _ ->
            Files.writeString(catalog, Files.readString(catalog) + " ")
            assertFailsWith<IllegalArgumentException> {
                CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.TEST)
            }
        }
    }

    @Test
    fun `a repository frozen at another base commit is rejected`() {
        withCatalog { catalog, freeze, checkouts, _ ->
            Files.writeString(freeze, Files.readString(freeze).replace("\"baseCommit\": \"", "\"baseCommit\": \"0"))
            assertFailsWith<IllegalArgumentException> {
                CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.TEST)
            }
        }
    }

    @Test
    fun `a frozen base commit that the checkout does not hold is rejected`() {
        withCatalog { catalog, freeze, checkouts, shas ->
            rebase(catalog, freeze, shas.getValue("app1"), "0".repeat(COMMIT_LENGTH))

            val failure = assertFailsWith<IllegalArgumentException> {
                CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.TEST)
            }

            assertContains(failure.message.orEmpty(), "Commits of app are missing")
        }
    }

    @Test
    fun `a frozen base commit that the scenario does not descend from is rejected`() {
        withCatalog { catalog, freeze, checkouts, shas ->
            rebase(catalog, freeze, shas.getValue("app1"), shas.getValue("app2"))

            val failure = assertFailsWith<IllegalArgumentException> {
                CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.TEST)
            }

            assertContains(failure.message.orEmpty(), "Scenario of app does not follow its base")
        }
    }

    @Test
    fun `a catalog of an unsupported schema version is rejected even when its freeze hash matches`() {
        withCatalog { catalog, freeze, checkouts, _ ->
            val supportedHash = CatalogFreeze.digest(Files.readAllBytes(catalog))
            val future = Files.readString(catalog).replace("\"schemaVersion\": 2", "\"schemaVersion\": 3")
            Files.writeString(catalog, future)
            val futureHash = CatalogFreeze.digest(future.toByteArray())
            Files.writeString(freeze, Files.readString(freeze).replace(supportedHash, futureHash))

            val failure = assertFailsWith<IllegalArgumentException> {
                CatalogManifestImporter(checkouts).import(catalog, freeze, RealSplit.TEST)
            }

            assertContains(failure.message.orEmpty(), "Unsupported catalog file: schemaVersion 3")
        }
    }

    @Test
    fun `line endings do not change the hash of a catalog`() {
        val text = "{\n  \"a\": 1\n}\n"
        assertEquals(
            CatalogFreeze.digest(text.toByteArray()),
            CatalogFreeze.digest(text.replace("\n", "\r\n").toByteArray()),
        )
    }

    private fun rebase(catalog: Path, freeze: Path, from: String, to: String) {
        val frozen = "\"baseCommit\": \"$from\""
        val moved = "\"baseCommit\": \"$to\""
        val previousHash = CatalogFreeze.digest(Files.readAllBytes(catalog))
        val updated = Files.readString(catalog).replace(frozen, moved)
        Files.writeString(catalog, updated)
        val hash = CatalogFreeze.digest(updated.toByteArray())
        Files.writeString(freeze, Files.readString(freeze).replace(frozen, moved).replace(previousHash, hash))
    }

    private fun withCatalog(block: (Path, Path, Path, Map<String, String>) -> Unit) {
        val work = Files.createTempDirectory(directory, "case")
        val checkouts = Files.createDirectory(work.resolve("checkouts"))
        CorpusRepository("api", Files.createDirectory(checkouts.resolve("api"))).use { api ->
            CorpusRepository("app", Files.createDirectory(checkouts.resolve("app"))).use { app ->
                val shas = mapOf(
                    "api1" to api.commit("one", mapOf("README.md" to "api one\n")),
                    "api2" to api.commit("two", mapOf("README.md" to "api two\n")),
                    "app1" to app.commit("one", mapOf("README.md" to "app one\n")),
                    "app2" to app.commit("two", mapOf("README.md" to "app two\n")),
                )
                val catalog = work.resolve("catalog.json")
                Files.writeString(catalog, CatalogSamples.catalog(shas))
                val freeze = work.resolve("freeze.json")
                val hash = CatalogFreeze.digest(Files.readAllBytes(catalog))
                Files.writeString(freeze, CatalogSamples.freeze(shas, hash))
                block(catalog, freeze, checkouts, shas)
            }
        }
    }

    private companion object {
        const val COMMIT_LENGTH = 40
    }
}
