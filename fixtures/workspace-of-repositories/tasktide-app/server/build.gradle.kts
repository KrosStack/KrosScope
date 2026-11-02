plugins {
    kotlin("jvm")
    id("com.squareup.wire")
}

wire {
    sourcePath {
        srcDir("$rootDir/../tasktide-spec/proto")
    }
    kotlin {}
}

tasks.processResources {
    from("$rootDir/../tasktide-web/dist") {
        into("static")
    }
}
