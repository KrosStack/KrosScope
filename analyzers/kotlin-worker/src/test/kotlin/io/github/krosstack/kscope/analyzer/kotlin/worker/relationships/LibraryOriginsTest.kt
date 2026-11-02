package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries.LibraryOrigin
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries.LibraryOrigins
import io.github.krosstack.kscope.model.SymbolEcosystem
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryOriginsTest {
    private fun maven(coordinate: String) = LibraryOrigin(SymbolEcosystem.MAVEN, coordinate)

    @Test
    fun `a jar in the Gradle cache is named by its group and artifact`() {
        val jar = "C:/Users/dev/.gradle/caches/modules-2/files-2.1/com.squareup/okio/3.9.0/0123abcd/okio-3.9.0.jar"
        assertEquals(maven("com.squareup:okio"), LibraryOrigins.ofJar(jar))
    }

    @Test
    fun `a jar in a local Maven repository is named by its group and artifact`() {
        val jar = "/home/dev/.m2/repository/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar"
        assertEquals(maven("org.slf4j:slf4j-api"), LibraryOrigins.ofJar(jar))
    }

    @Test
    fun `a Kotlin standard library jar anywhere is named after the artifact`() {
        val stdlib = LibraryOrigins.ofJar("/opt/libs/kotlin-stdlib-2.4.20.jar")
        val jdk8 = LibraryOrigins.ofJar("lib/kotlin-stdlib-jdk8-1.8.0.jar")
        assertEquals(maven("org.jetbrains.kotlin:kotlin-stdlib"), stdlib)
        assertEquals(maven("org.jetbrains.kotlin:kotlin-stdlib-jdk8"), jdk8)
    }

    @Test
    fun `any other jar and a missing location have an unknown origin without a coordinate`() {
        val unknown = LibraryOrigin(SymbolEcosystem.UNKNOWN, null)
        assertEquals(unknown, LibraryOrigins.ofJar("/sdk/platforms/android-35/android.jar"))
        assertEquals(unknown, LibraryOrigins.ofJar(null))
    }
}
