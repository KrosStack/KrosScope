import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity
import io.github.krosstack.kscope.conventions.DetektSlots
import io.github.krosstack.kscope.conventions.GitTestEnvironment
import io.github.krosstack.kscope.conventions.VerifyFileLength
import io.github.krosstack.kscope.conventions.VerifyModuleDependencies
import io.github.krosstack.kscope.conventions.VerifyPackageSize
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

private val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun catalogLibrary(alias: String) = catalog.findLibrary(alias).get()

private val maxSourceFileLines = 400

private val maxFilesPerPackage = 20

// The Detekt Gradle plugin is not applied: its 2.0.0-alpha.6 release still calls Configuration.setVisible, which
// Gradle 9 reports as deprecated for removal in Gradle 11. Only its task type is used, with the plugin's own rules.
val detektRuntime = configurations.resolvable("detekt")
val detektPlugins = configurations.resolvable("detektPlugins")
val detektDependencies = configurations.dependencyScope("detektDependencies")
val detektPluginDependencies = configurations.dependencyScope("detektPluginDependencies")
detektRuntime.configure { extendsFrom(detektDependencies.get()) }
detektPlugins.configure { extendsFrom(detektPluginDependencies.get()) }

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(platform(catalogLibrary("junit-bom")))
    testImplementation(kotlin("test"))
    testRuntimeOnly(catalogLibrary("junit-platform-launcher"))
    add(detektDependencies.name, catalogLibrary("detekt-cli"))
    add(detektPluginDependencies.name, catalogLibrary("detekt-rules-ktlint-wrapper"))
    add(detektPluginDependencies.name, project(":tools:detekt-rules"))
}

tasks.test {
    setEnvironment(GitTestEnvironment.isolated(environment))
    environment("KSCOPE_SERVICE_HOME", layout.buildDirectory.dir("kscope-service-home").get().asFile.path)
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val detektConfig = isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml")
val detektJdk = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath }
val detektReportsDirectory = layout.buildDirectory.dir("reports/detekt")
val detektKotlinSources = kotlin.sourceSets

// Detekt runs inside the Gradle daemon. Several type-resolving runs at once exhaust its heap, so the build runs at most
// two at a time.
val detektSlots = gradle.sharedServices.registerIfAbsent("detektSlots", DetektSlots::class) {
    maxParallelUsages = 2
}

tasks.withType<Detekt>().configureEach {
    usesService(detektSlots)
    detektClasspath.from(detektRuntime)
    pluginClasspath.from(detektPlugins)
    config.from(detektConfig)
    jdkHome.set(detektJdk)
    buildUponDefaultConfig.set(true)
    failOnSeverity.set(FailOnSeverity.Info)
    parallel.set(true)
    debug.set(false)
    ignoreFailures.set(false)
    disableDefaultRuleSets.set(false)
    allRules.set(false)
    autoCorrect.convention(false)
    noJdk.convention(false)
    multiPlatformEnabled.convention(false)
    include("**/*.kt", "**/*.kts")
    val reportName = name
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
        markdown.required.set(true)
        checkstyle.outputLocation.set(detektReportsDirectory.map { it.file("$reportName.xml") })
        html.outputLocation.set(detektReportsDirectory.map { it.file("$reportName.html") })
        sarif.outputLocation.set(detektReportsDirectory.map { it.file("$reportName.sarif") })
        markdown.outputLocation.set(detektReportsDirectory.map { it.file("$reportName.md") })
    }
}

val detekt = tasks.register<Detekt>("detekt") {
    description = "Runs source analysis without type resolution."
    setSource(detektKotlinSources.map { it.kotlin })
}

sourceSets.configureEach {
    val sources = this
    val compilation = tasks.named<KotlinCompile>(getCompileTaskName("kotlin"))
    val analysis = tasks.register<Detekt>("detekt${name.replaceFirstChar { it.uppercaseChar() }}") {
        description = "Runs analysis of ${sources.name} with type resolution."
        setSource(detektKotlinSources.named(sources.name).map { it.kotlin })
        classpath.from(sources.output.classesDirs, compilation.map { it.libraries })
        friendPaths.from(sources.output.classesDirs, compilation.map { it.friendPaths })
        apiVersion.set(compilation.flatMap { it.compilerOptions.apiVersion }.map { it.version })
        languageVersion.set(compilation.flatMap { it.compilerOptions.languageVersion }.map { it.version })
        jvmTarget.set(compilation.flatMap { it.compilerOptions.jvmTarget }.map { it.target })
        freeCompilerArgs.set(compilation.flatMap { it.compilerOptions.freeCompilerArgs })
        optIn.set(compilation.flatMap { it.compilerOptions.optIn })
        noJdk.set(compilation.flatMap { it.compilerOptions.noJdk })
    }
    tasks.check { dependsOn(analysis) }
}

private val ownModulePath = project.path

val verifyModuleDependencies = tasks.register<VerifyModuleDependencies>("verifyModuleDependencies") {
    group = "verification"
    description = "Fails when this module declares a project dependency that the module rules do not allow."
    modulePath = ownModulePath
    declaredProjectDependencies = provider {
        configurations.filter { it.name != detektPluginDependencies.name }
            .flatMap { it.dependencies.withType<ProjectDependency>() }.map { it.path }
            .filter { it != ownModulePath }
            .toSet()
    }
}

val verifyFileLength = tasks.register<VerifyFileLength>("verifyFileLength") {
    group = "verification"
    description = "Fails when a Kotlin source file exceeds the maximum line count."
    sources.from(layout.projectDirectory.dir("src"))
    maxLines = maxSourceFileLines
}

val verifyPackageSize = tasks.register<VerifyPackageSize>("verifyPackageSize") {
    group = "verification"
    description = "Fails when a source directory holds more Kotlin files than its limit."
    sources.from(layout.projectDirectory.dir("src/main"), layout.projectDirectory.dir("src/test"))
    moduleDirectory = layout.projectDirectory
    maxFiles = maxFilesPerPackage
}

tasks.check {
    dependsOn(verifyModuleDependencies, verifyFileLength, verifyPackageSize, detekt)
}
