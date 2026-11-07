package io.github.krosstack.kscope.service.components

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComponentRecoveryTest : ComponentInstallerFixture() {
    @TestFactory
    fun `failed install preserves installed versions containing reserved infixes`() =
        listOf("1.installing-release", "1.replacing-release").map { version ->
            dynamicTest("installed version $version survives a failed install") {
                val home = root.resolve(version)
                val target = Files.createDirectories(home.resolve("sample/$version"))
                InstalledComponent.write(InstalledComponent("sample", version, target, listOf("data")))
                Files.writeString(target.resolve("data"), "keep")
                val source = Files.createDirectories(root.resolve("offline"))
                val installer = ComponentInstaller(ComponentDirectory(home))
                installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
                assertEquals("keep", Files.readString(target.resolve("data")))
            }
        }

    @Test
    fun `next install removes abandoned owned staging`() {
        val staging = Files.createDirectories(root.resolve("sample/1.installing-old"))
        Files.writeString(staging.resolve("kscope-staging.json"), """{"name":"sample","version":"1"}""")
        Files.writeString(staging.resolve("partial"), "partial")
        val source = Files.createDirectories(root.resolve("offline"))
        val installer = ComponentInstaller(ComponentDirectory(root))
        installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
        assertFalse(Files.exists(staging))
    }

    @Test
    fun `next install preserves staging shaped directory without ownership`() {
        val staging = Files.createDirectories(root.resolve("sample/1.installing-unrelated"))
        val source = Files.createDirectories(root.resolve("offline"))
        val installer = ComponentInstaller(ComponentDirectory(root))
        installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
        assertTrue(Files.exists(staging))
    }

    @Test
    fun `next install preserves installed staging shaped directory even with staging ownership`() {
        val version = "1.installing-old"
        val target = Files.createDirectories(root.resolve("sample/$version"))
        Files.writeString(target.resolve("kscope-staging.json"), """{"name":"sample","version":"1"}""")
        InstalledComponent.write(InstalledComponent("sample", version, target, emptyList()))
        val source = Files.createDirectories(root.resolve("offline"))
        val installer = ComponentInstaller(ComponentDirectory(root))
        installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
        assertTrue(Files.exists(target.resolve("kscope-component.json")))
    }

    @TestFactory
    fun `next install preserves directories without exact staging shape and matching ownership`() = listOf(
        "1.installing-",
        "1.replacing-old.installing-token",
        "1.installing-old.installing-token",
        "2.installing-token",
    ).map { folder ->
        dynamicTest("directory $folder survives the next install") {
            val home = root.resolve(folder)
            val target = Files.createDirectories(home.resolve("sample/$folder"))
            Files.writeString(target.resolve("kscope-staging.json"), """{"name":"sample","version":"1"}""")
            val source = Files.createDirectories(root.resolve("offline"))
            val installer = ComponentInstaller(ComponentDirectory(home))
            installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
            assertTrue(Files.exists(target))
        }
    }

    @TestFactory
    fun `next install preserves foreign and corrupt staging markers`() =
        listOf("""{"name":"foreign","version":"1"}""", "invalid json").mapIndexed { index, marker ->
            dynamicTest("directory with marker $marker survives the next install") {
                val home = root.resolve("case-$index")
                val target = Files.createDirectories(home.resolve("sample/1.installing-old"))
                Files.writeString(target.resolve("kscope-staging.json"), marker)
                val source = Files.createDirectories(root.resolve("offline"))
                val installer = ComponentInstaller(ComponentDirectory(home))
                installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
                assertTrue(Files.exists(target))
            }
        }

    @Test
    fun `installed component retains staging ownership`() {
        val source = Files.createDirectories(root.resolve("offline"))
        val bytes = zip("data")
        Files.write(source.resolve("bundle.zip"), bytes)
        Files.write(source.resolve("LICENSE"), plain)
        val installer = ComponentInstaller(ComponentDirectory(root))
        installer.install(entry(bytes), "linux-x64", InstallSource.Directory(source))
        assertEquals(
            """{"name":"sample","version":"1"}""",
            Files.readString(root.resolve("sample/1/kscope-staging.json")),
        )
    }

    @Test
    fun `failed install removes its staging directory`() {
        val source = Files.createDirectories(root.resolve("offline"))
        val installer = ComponentInstaller(ComponentDirectory(root))
        installer.install(entry(zip("data")), "linux-x64", InstallSource.Directory(source))
        assertEmpty()
    }

    @Test
    fun `archive cannot overwrite staging ownership`() {
        val source = Files.createDirectories(root.resolve("offline"))
        val bytes = zip("KSCOPE-STAGING.JSON")
        Files.write(source.resolve("bundle.zip"), bytes)
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        val installer = ComponentInstaller(ComponentDirectory(root))
        val result = installer.install(entry, "linux-x64", InstallSource.Directory(source))
        assertIs<InstallResult.UnsafeEntry>(result)
    }

    @Test
    fun `rejected archive leaves no staging directory`() {
        val source = Files.createDirectories(root.resolve("offline"))
        val bytes = zip("KSCOPE-STAGING.JSON")
        Files.write(source.resolve("bundle.zip"), bytes)
        val entry = entry(bytes).let { it.copy(files = it.files.take(1)) }
        val installer = ComponentInstaller(ComponentDirectory(root))
        installer.install(entry, "linux-x64", InstallSource.Directory(source))
        assertEmpty()
    }
}
