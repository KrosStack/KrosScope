import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

plugins {
    id("kscope.kotlin-application")
}

val kotlinWorkerDependencies = configurations.dependencyScope("kotlinWorkerDependencies")

// The Kotlin worker runs in its own JVM and is shipped beside the application, not on its classpath.
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
    implementation(project(":service"))
    implementation(project(":benchmark"))
    implementation(libs.clikt)
    implementation(libs.jna)
    implementation(libs.tomlj)
    add(kotlinWorkerDependencies.name, project(":analyzers:kotlin-worker"))
}

application {
    applicationName = "kscope"
    mainClass = "io.github.krosstack.kscope.cli.MainKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

// The generated Windows script lists every jar in CLASSPATH, which cmd.exe cuts off at 8191 characters when the archive
// is unpacked into a deep directory; the Java launcher expands the wildcard instead.
tasks.startScripts {
    doLast {
        val classpathLine = Regex("(?m)^set CLASSPATH=.*$")
        val wildcard = """set CLASSPATH=%APP_HOME%\lib\*"""
        windowsScript.writeText(windowsScript.readText().replace(classpathLine) { wildcard })
    }
}

val repositoryRoot = rootProject.layout.projectDirectory

distributions {
    main {
        contents {
            from(repositoryRoot.file("LICENSE"))
            from(repositoryRoot.file("NOTICE"))
            from(repositoryRoot.file("docs/third-party-licenses.md")) {
                rename { "THIRD-PARTY-LICENSES.md" }
            }
            into("licenses") {
                from(repositoryRoot.dir("licenses"))
            }
            into("workers/kotlin/lib") {
                from(kotlinWorkerRuntime)
            }
        }
    }
}

tasks.test {
    val workerClasspath = files(kotlinWorkerRuntime)
    val installed = layout.buildDirectory.dir("install/kscope")
    inputs.files(workerClasspath).withPropertyName("kotlinWorkerClasspath").withNormalizer(ClasspathNormalizer::class)
    dependsOn(tasks.installDist)
    inputs.dir(installed).withPropertyName("installedDistribution").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kscope.distribution", installed.get().asFile.absolutePath)
    doFirst { systemProperty("kscope.worker.kotlin.classpath", workerClasspath.asPath) }
}
