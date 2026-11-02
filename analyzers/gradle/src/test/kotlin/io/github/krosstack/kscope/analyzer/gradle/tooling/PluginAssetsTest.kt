package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginAssetsTest {
    private val directory: Path = Files.createTempDirectory("kscope-plugin-assets")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `the plugin jar and the init script are extracted below a directory named by their hash`() {
        val assets = PluginAssets.extract(directory)
        assertTrue(Files.size(assets.jar) > 0)
        assertTrue(assets.jar.startsWith(directory.resolve("plugin")))
        assertTrue(assets.jar.parent.fileName.toString().let { assets.hash.startsWith(it) })
        assertEquals(assets.jar.parent, assets.initScript.parent)
    }

    @Test
    fun `extracting twice yields the same files`() {
        val first = PluginAssets.extract(directory)
        val second = PluginAssets.extract(directory)
        assertEquals(first.jar, second.jar)
        assertEquals(first.hash, second.hash)
    }

    @Test
    fun `the plugin jar contains the model plugin and its model builder`() {
        val assets = PluginAssets.extract(directory)
        ZipFile(assets.jar.toFile()).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            assertTrue("io/github/krosstack/kscope/gradle/model/KscopeModelPlugin.class" in entries)
            assertTrue("io/github/krosstack/kscope/gradle/model/KscopeModelBuilder.class" in entries)
        }
    }

    @Test
    fun `the init script applies the plugin only when its activation property is set`() {
        val script = Files.readString(PluginAssets.extract(directory).initScript)
        val property = "System.getProperty('kscope.gradle.model.pluginJar')"
        val apply = script.indexOf("pluginManager.apply")
        assertTrue(apply > 0)
        val guard = script.substring(script.lastIndexOf("if (", apply), apply)
        assertTrue(property in guard)
    }
}
