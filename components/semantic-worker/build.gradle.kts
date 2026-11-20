plugins {
    id("kscope.kotlin-application")
}

application {
    mainClass = "io.github.krosstack.kscope.semantic.worker.Main"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:spi"))
    implementation(libs.onnxruntime)
}

val cudaRuntime = configurations.resolvable("cudaRuntime")
dependencies { add(cudaRuntime.name, libs.onnxruntimeGpu) }

val cudaNatives = tasks.register<Sync>("extractCudaNatives") {
    from(cudaRuntime.map { files -> files.map { zipTree(it) } }) {
        include("ai/onnxruntime/native/win-x64/*.dll", "ai/onnxruntime/native/linux-x64/*.so")
        eachFile {
            val platform = if (relativePath.segments[3] == "win-x64") "windows-x64" else "linux-x64"
            path = "$platform/$name"
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("cuda-native"))
}

val nativeLibraries = tasks.register<Sync>("extractNatives") {
    val runtime = configurations.runtimeClasspath
    from(runtime.map { files -> files.filter { it.name.startsWith("onnxruntime-") }.map { zipTree(it) } }) {
        include("ai/onnxruntime/native/*/*.dll", "ai/onnxruntime/native/*/*.so", "ai/onnxruntime/native/*/*.dylib")
        eachFile {
            val platforms = mapOf(
                "win-x64" to "windows-x64",
                "linux-x64" to "linux-x64",
                "linux-aarch64" to "linux-aarch64",
                "osx-aarch64" to "macos-arm64",
            )
            val platform = platforms[relativePath.segments[3]]
            if (platform == null) exclude() else path = "$platform/${name}"
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("native"))
}

val repositoryRoot = rootProject.layout.projectDirectory
distributions {
    main {
        distributionBaseName = "semantic-worker"
        contents {
            into("/")
            exclude("semantic-worker", "semantic-worker.bat")
            into("native") { from(nativeLibraries) }
            into("licenses") { from(repositoryRoot.dir("licenses")) }
            from(repositoryRoot.file("LICENSE"), repositoryRoot.file("NOTICE"))
            from(repositoryRoot.file("docs/third-party-licenses.md")) { rename { "THIRD-PARTY-LICENSES.md" } }
        }
    }
    create("cuda") {
        distributionBaseName = "semantic-worker-cuda"
        contents {
            into("/")
            into("lib") {
                from(tasks.jar)
                from(configurations.runtimeClasspath) { exclude("onnxruntime-*.jar") }
                from(cudaRuntime)
            }
            into("native") { from(cudaNatives) }
            into("licenses") { from(repositoryRoot.dir("licenses")) }
            from(repositoryRoot.file("LICENSE"), repositoryRoot.file("NOTICE"))
            from(repositoryRoot.file("docs/third-party-licenses.md")) { rename { "THIRD-PARTY-LICENSES.md" } }
        }
    }
}

tasks.named<Zip>("cudaDistZip") {
    destinationDirectory = layout.buildDirectory.dir("cuda-distributions")
}

tasks.register<Test>("cudaIntegrationTest") {
    description = "Compares optional CUDA inference with CPU float inference in child JVMs."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*CudaIntegrationTest") }
}

tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("cudaDistZip"))
    val cudaArchive = tasks.named<Zip>("cudaDistZip").flatMap { it.archiveFile }
    inputs.file(cudaArchive).withPropertyName("cudaArchive").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kscope.semantic.cuda.archive", cudaArchive.get().asFile.path)
    val cpuClasspath = sourceSets.main.get().runtimeClasspath
    val gpuClasspath = files(sourceSets.main.get().output, cpuClasspath.filterNot { it.name.startsWith("onnxruntime-") }, cudaRuntime)
    doFirst {
        systemProperty("kscope.semantic.cpu.classpath", cpuClasspath.asPath)
        systemProperty("kscope.semantic.cuda.classpath", gpuClasspath.asPath)
    }
    listOf("libraries", "model", "cpuModel", "javaHome").forEach { key ->
        val property = "kscope.semantic.cuda.$key"
        val value = providers.systemProperty(property)
        inputs.property(property, value.orElse("absent"))
        value.orNull?.let { systemProperty(property, it) }
    }
}

tasks.test {
    dependsOn(tasks.distZip)
    val archive = tasks.distZip.flatMap { it.archiveFile }
    inputs.file(archive).withPropertyName("semanticArchive").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kscope.semantic.archive", archive.get().asFile.path)
    val model = providers.environmentVariable("KSCOPE_SEMANTIC_MODEL")
    inputs.property("semanticModel", model.orElse("absent"))
    environment("KSCOPE_SEMANTIC_MODEL", model.orElse("").get())
}
