package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The build facts that the view binding and build config synthesizers read, in the forms a script may write them. */
class BuildFeaturesStaticTest {
    private val tree = StaticWorkspace("android-dotted")
    private val app = tree.provided().module(":app")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `a build feature set with a dotted name in the android block is enabled like one in a buildFeatures block`() {
        assertEquals("true", app.attributes["android.buildFeatures.viewBinding"])
        assertEquals("true", app.attributes["android.buildFeatures.buildConfig"])
    }

    @Test
    fun `a build feature that is set to false is not an attribute`() {
        assertEquals(null, app.attributes["android.buildFeatures.compose"])
    }

    @Test
    fun `the flavor dimensions are an attribute in the order they are declared`() {
        assertEquals("tier,store", app.attributes["android.flavorDimensions"])
    }
}
