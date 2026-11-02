import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

plugins {
    id("kscope.kotlin-library")
}

val kotlinWorkerDependencies = configurations.dependencyScope("kotlinWorkerDependencies")

// The worker runs in its own JVM; the tests start it from its runtime classpath, which Gradle resolves here.
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
    api(project(":core:model"))
    api(project(":core:spi"))
    add(kotlinWorkerDependencies.name, project(":analyzers:kotlin-worker"))
}

tasks.test {
    val workerClasspath = files(kotlinWorkerRuntime)
    val testClasspath = sourceSets.test.get().runtimeClasspath
    inputs.files(workerClasspath).withPropertyName("kotlinWorkerClasspath").withNormalizer(ClasspathNormalizer::class)
    doFirst {
        systemProperty("kscope.worker.kotlin.classpath", workerClasspath.asPath)
        systemProperty("kscope.test.classpath", testClasspath.asPath)
    }
}
