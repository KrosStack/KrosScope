package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.block
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.writeKotlinScript
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildFeatureOverrideStaticTest {
    private val tree = StaticWorkspace("minimal")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun featuresOf(script: String): Map<String, String> {
        tree.write("settings.gradle", "include ':lib'\nincludeBuild 'build-logic'\n")
        val enabled = mapOf("viewBinding" to "true", "buildConfig" to "true")
        tree.writeKotlinScript(
            "build-logic/src/main/kotlin/acme.android.gradle.kts",
            block("android", block("buildFeatures", assignments = enabled)),
        )
        tree.write("lib/build.gradle", script)
        return tree.provided().module(":lib").attributes.filterKeys { it.startsWith("android.buildFeatures.") }
    }

    @Test
    fun `a build feature that the module sets to false overrides the convention plugin that enables it`() {
        val features = featuresOf(
            "plugins {\n    id 'com.android.library'\n    id 'acme.android'\n}\n" +
                "android {\n    buildFeatures {\n        viewBinding = false\n    }\n}\n",
        )
        assertEquals(mapOf("android.buildFeatures.buildConfig" to "true"), features)
    }

    @Test
    fun `a build feature that the module sets to true again after disabling it is enabled`() {
        val features = featuresOf(
            "plugins {\n    id 'com.android.library'\n    id 'acme.android'\n}\n" +
                "android {\n    buildFeatures {\n        viewBinding = false\n    }\n}\n" +
                "android {\n    buildFeatures {\n        viewBinding = true\n    }\n}\n",
        )
        assertEquals(
            mapOf("android.buildFeatures.buildConfig" to "true", "android.buildFeatures.viewBinding" to "true"),
            features,
        )
    }
}
