package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.nio.file.Path

/**
 * The JDK that a trusted scan runs Gradle with, when the environment variable `KSCOPE_GRADLE_JAVA_HOME` names one.
 * Without it Gradle runs with the JVM that runs the tool, unless the build says otherwise, which a build that needs a
 * newer JDK than the tool does not survive.
 */
internal object GradleJavaHome {
    const val VARIABLE = "KSCOPE_GRADLE_JAVA_HOME"

    fun from(environment: (String) -> String?): Path? =
        environment(VARIABLE)?.trim()?.takeIf(String::isNotEmpty)?.let(Path::of)

    fun fromEnvironment(): Path? = from(System::getenv)
}
