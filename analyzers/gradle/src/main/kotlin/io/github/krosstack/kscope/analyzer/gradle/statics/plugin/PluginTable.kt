package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.mapping.ComposeResourceAttributes

/** What the static provider knows about plugin ids: their canonical form, the plugins they imply, their kind. */
internal object PluginTable {
    const val KOTLIN_MULTIPLATFORM = "org.jetbrains.kotlin.multiplatform"
    const val ANDROID_KOTLIN_LIBRARY = "com.android.kotlin.multiplatform.library"
    const val JAVA_TEST_FIXTURES = "java-test-fixtures"

    private val legacyIds = mapOf(
        "kotlin" to "org.jetbrains.kotlin.jvm",
        "kotlin-android" to "org.jetbrains.kotlin.android",
        "kotlin-multiplatform" to KOTLIN_MULTIPLATFORM,
        "kotlin-parcelize" to "org.jetbrains.kotlin.plugin.parcelize",
        "kotlin-kapt" to "org.jetbrains.kotlin.kapt",
        "kotlinx-serialization" to "org.jetbrains.kotlin.plugin.serialization",
        "kotlin-dsl" to "org.gradle.kotlin.kotlin-dsl",
    )

    private val implied = mapOf("java-library" to listOf("java"))

    val androidPlugins = setOf("com.android.application", "com.android.library")

    val jvmPlugins = setOf(
        "org.jetbrains.kotlin.jvm",
        "java",
        "java-library",
        "application",
        "org.gradle.kotlin.kotlin-dsl",
        "groovy",
        "java-gradle-plugin",
    )

    val publishingPlugins = setOf("maven-publish", "com.vanniktech.maven.publish", "com.vanniktech.maven.publish.base")

    private val understood = androidPlugins + jvmPlugins + publishingPlugins + setOf(
        ANDROID_KOTLIN_LIBRARY,
        KOTLIN_MULTIPLATFORM,
        JAVA_TEST_FIXTURES,
        "org.jetbrains.kotlin.android",
        "org.jetbrains.kotlin.plugin.serialization",
        "org.jetbrains.kotlin.plugin.parcelize",
        "org.jetbrains.kotlin.plugin.compose",
        ComposeResourceAttributes.PLUGIN_ID,
        "app.cash.sqldelight",
        "com.squareup.wire",
        "com.google.devtools.ksp",
    )

    fun canonical(id: String): String = legacyIds[id] ?: id

    fun impliedBy(id: String): List<String> = implied[id].orEmpty()

    fun isUnderstood(id: String): Boolean = id in understood
}
