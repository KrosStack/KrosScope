import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

plugins {
    id("kscope.kotlin-library")
    application
}

val kotlinWorkerDependencies = configurations.dependencyScope("kotlinWorkerDependencies")

// Scans run the Kotlin worker in a child JVM, which needs its own runtime classpath.
val kotlinWorkerRuntime = configurations.resolvable("kotlinWorkerRuntime") {
    extendsFrom(kotlinWorkerDependencies.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:spi"))
    implementation(project(":service"))
    implementation(libs.sqlite.jdbc)
    add(kotlinWorkerDependencies.name, project(":analyzers:kotlin-worker"))
}

application {
    mainClass = "io.github.krosstack.kscope.benchmark.MainKt"
}

val integration = providers.gradleProperty("kscope.integration").orNull == "true"
val referenceWorkspace = rootProject.layout.projectDirectory.dir("fixtures/reference-workspace").asFile.path
val goldDirectory = layout.projectDirectory.dir("reference").asFile.path
val workspaceFixture = rootProject.layout.projectDirectory.dir("fixtures/workspace-of-repositories").asFile.path
val workspaceGold = layout.projectDirectory.dir("workspace").asFile.path

tasks.withType<Test>().configureEach {
    inputs.files(fileTree(referenceWorkspace) { exclude("**/build/**", "**/.gradle/**", "**/.kscope/**", "**/.kotlin/**") })
        .withPropertyName("referenceWorkspace").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(workspaceFixture).withPropertyName("workspaceFixture").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(workspaceGold).withPropertyName("workspaceGold").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("kscope.workspaceFixture", workspaceFixture)
    systemProperty("kscope.workspaceGold", workspaceGold)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/schemas"))
        .withPropertyName("apiSchemas").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.test {
    val workerClasspath = files(kotlinWorkerRuntime)
    inputs.files(workerClasspath).withPropertyName("kotlinWorkerClasspath").withNormalizer(ClasspathNormalizer::class)
    inputs.dir(goldDirectory).withPropertyName("gold").withPathSensitivity(PathSensitivity.RELATIVE)
    useJUnitPlatform {
        if (!integration) excludeTags("gradle-integration")
    }
    systemProperty("kscope.fixture", referenceWorkspace)
    systemProperty("kscope.gold", goldDirectory)
    doFirst { systemProperty("kscope.worker.kotlin.classpath", workerClasspath.asPath) }
}

tasks.run {
    val workerClasspath = files(kotlinWorkerRuntime)
    workingDir = rootProject.layout.projectDirectory.asFile
    systemProperty("kscope.fixture", referenceWorkspace)
    systemProperty("kscope.gold", goldDirectory)
    doFirst { systemProperty("kscope.worker.kotlin.classpath", workerClasspath.asPath) }
}
