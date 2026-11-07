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
            exclude("bin/**")
            into("native") { from(nativeLibraries) }
            into("licenses") { from(repositoryRoot.dir("licenses")) }
            from(repositoryRoot.file("LICENSE"), repositoryRoot.file("NOTICE"))
            from(repositoryRoot.file("docs/third-party-licenses.md")) { rename { "THIRD-PARTY-LICENSES.md" } }
        }
    }
}

tasks.test {
    dependsOn(tasks.distZip)
    val archive = tasks.distZip.flatMap { it.archiveFile }
    inputs.file(archive).withPropertyName("semanticArchive").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kscope.semantic.archive", archive.get().asFile.path)
    val model = providers.environmentVariable("KSCOPE_SEMANTIC_MODEL")
    inputs.property("semanticModel", model.orElse("absent"))
}
