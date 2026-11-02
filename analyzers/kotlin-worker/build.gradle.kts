plugins {
    id("kscope.kotlin-application")
}

application {
    mainClass = "io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerMain"
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "org.jetbrains.kotlin.analysis.api.KaExperimentalApi",
            "org.jetbrains.kotlin.analysis.api.KaNonPublicApi",
            "org.jetbrains.kotlin.analysis.api.KaImplementationDetail",
            "org.jetbrains.kotlin.analysis.api.KaIdeApi",
            "org.jetbrains.kotlin.analysis.api.KaPlatformInterface",
            "org.jetbrains.kotlin.psi.KtExperimentalApi",
        )
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:spi"))

    libs.bundles.kotlin.analysis.api.get().forEach { artifact ->
        implementation("${artifact.module}:${artifact.versionConstraint.requiredVersion}") { isTransitive = false }
    }
    implementation(libs.kotlin.analysis.api.compiler) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation(libs.caffeine)
    implementation(libs.intellij.coroutines)
}

tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootProject.layout.projectDirectory.dir("fixtures/reference-workspace")) { exclude("**/build/**", "**/.gradle/**", "**/.kscope/**", "**/.kotlin/**") })
        .withPropertyName("referenceWorkspace").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.test {
    maxHeapSize = "1536m"
    val workerClasspath = sourceSets.main.get().runtimeClasspath
    doFirst {
        (this as Test).systemProperty("kscope.worker.classpath", workerClasspath.asPath)
    }
}
