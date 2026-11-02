import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

plugins {
    id("kscope.kotlin-library")
}

val kotlinWorkerDependencies = configurations.dependencyScope("kotlinWorkerDependencies")

// The scans of the tests run the Kotlin worker, which needs its own runtime classpath.
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
    api(project(":intelligence:retrieval"))
    api(project(":intelligence:context"))
    implementation(project(":core:model"))
    implementation(project(":core:spi"))
    implementation(project(":core:engine"))
    implementation(project(":analyzers:android"))
    implementation(project(":analyzers:compose"))
    implementation(project(":analyzers:gradle"))
    implementation(project(":analyzers:history"))
    implementation(project(":analyzers:jni"))
    implementation(project(":analyzers:kotlin"))
    implementation(project(":analyzers:schema"))
    implementation(libs.tomlj)
    add(kotlinWorkerDependencies.name, project(":analyzers:kotlin-worker"))
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
}

tasks.processResources {
    val toolVersion = project.version.toString()
    inputs.property("toolVersion", toolVersion)
    filesMatching("kscope-version.properties") {
        expand("toolVersion" to toolVersion)
    }
}

val integration = providers.gradleProperty("kscope.integration").orNull == "true"

tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootProject.layout.projectDirectory.dir("fixtures/reference-workspace")) { exclude("**/build/**", "**/.gradle/**", "**/.kscope/**", "**/.kotlin/**") })
        .withPropertyName("referenceWorkspace").withPathSensitivity(PathSensitivity.RELATIVE)
    val workspaceFixture = rootProject.layout.projectDirectory.dir("fixtures/workspace-of-repositories")
    val workspaceOutputs = rootProject.layout.projectDirectory.file("benchmark/workspace/outputs.json")
    inputs.dir(workspaceFixture).withPropertyName("workspaceFixture").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(workspaceOutputs).withPropertyName("workspaceOutputs").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("kscope.workspaceFixture", workspaceFixture.asFile.path)
    systemProperty("kscope.workspaceOutputs", workspaceOutputs.asFile.path)
}

tasks.test {
    val workerClasspath = files(kotlinWorkerRuntime)
    inputs.files(workerClasspath).withPropertyName("kotlinWorkerClasspath").withNormalizer(ClasspathNormalizer::class)
    useJUnitPlatform {
        if (!integration) excludeTags("gradle-integration")
    }
    systemProperty("kscope.fixture", rootProject.layout.projectDirectory.dir("fixtures/reference-workspace").asFile.path)
    doFirst { systemProperty("kscope.worker.kotlin.classpath", workerClasspath.asPath) }
}
