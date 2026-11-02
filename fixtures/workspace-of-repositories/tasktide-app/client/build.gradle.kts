plugins {
    kotlin("jvm")
    id("org.openapi.generator")
    id("com.squareup.wire")
}

dependencies {
    implementation("sample.tasktide:format:1.2.0")
    implementation(files("libs/tasktide-text-1.0.0.jar"))
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../tasktide-spec/openapi/tasktide.yaml")
    packageName.set("sample.tasktide.api")
}

wire {
    sourcePath {
        srcDir("$rootDir/../tasktide-spec/proto")
    }
    kotlin {}
}
