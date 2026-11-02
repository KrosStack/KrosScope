import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(17)
}

dependencies {
    add("testImplementation", catalog.findLibrary("junit").get())
    add("testImplementation", catalog.findLibrary("kotlin-test").get())
    add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
