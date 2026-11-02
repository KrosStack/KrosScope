import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("kscope.jvm-base")
}

kotlin {
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

dependencies {
    compileOnly(gradleApi())

    testImplementation(gradleApi())
}

tasks.test {
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
