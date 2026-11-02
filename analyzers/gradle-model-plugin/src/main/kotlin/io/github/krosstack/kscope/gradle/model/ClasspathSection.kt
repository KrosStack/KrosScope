package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import java.io.File

/**
 * Best-effort compile classpaths: for each JVM or Android compilation the jars and class directories that exist on
 * disk. A failure to resolve one classpath is recorded as an error of the section and never fails the model. Paths
 * are interned in one table per project, because variants and compilations share most of their entries.
 */
internal class ClasspathSection(private val project: Project, private val errors: SectionErrors) {
    private val paths = LinkedHashMap<String, Int>()
    private val entries = mutableListOf<Map<String, Any?>>()

    fun addJavaSourceSets() {
        val extension = project.extensions.findByType(JavaPluginExtension::class.java) ?: return
        extension.sourceSets.forEach { sourceSet ->
            add("java", sourceSet.name, listOf(sourceSet.name)) { sourceSet.compileClasspath.files }
        }
    }

    fun addKotlinCompilations(targets: List<Any>, bootClasspath: List<String>) {
        targets.filter { Values.text(Reflect.get(it, "platformType")) in JVM_PLATFORMS }.forEach { target ->
            val targetName = Values.text(Reflect.get(target, "name"))
            val isAndroid = Values.text(Reflect.get(target, "platformType")) == ANDROID_PLATFORM
            val boot = if (isAndroid) bootClasspath else emptyList()
            KotlinSection.compilations(target).forEach { compilation ->
                val name = "$targetName/${Values.text(Reflect.get(compilation, "name"))}"
                val sourceSets = Values.names(Reflect.get(compilation, "allKotlinSourceSets"))
                add("kotlin-compilation", name, sourceSets) {
                    boot.map(::File) + (Reflect.get(compilation, "compileDependencyFiles") as FileCollection).files
                }
            }
        }
    }

    fun addAndroidVariants(variants: List<Map<String, Any?>>, bootClasspath: List<String>) {
        val boot = bootClasspath.map(::File)
        variants.forEach { variant ->
            val name = variant["name"].toString()
            val sourceSets = variant["sourceSets"] as? Map<*, *> ?: return@forEach
            COMPONENT_CONFIGURATIONS.forEach { (component, suffix) ->
                val names = (sourceSets[component] as? List<*>).orEmpty().map { it.toString() }
                if (names.isNotEmpty()) {
                    add("android-variant", "$name/$component", names) { boot + androidFiles(name + suffix) }
                }
            }
        }
    }

    fun describe(): Map<String, Any?> = linkedMapOf("files" to paths.keys.toList(), "entries" to entries)

    private fun add(kind: String, name: String, sourceSets: List<String>, files: () -> Collection<File>) {
        val resolved = errors.guard("classpaths", name) { files() } ?: return
        val indices = resolved.filter { it.exists() }.map(Values::slashPath).distinct()
            .map { path -> paths.getOrPut(path) { paths.size } }
        entries += linkedMapOf("kind" to kind, "name" to name, "sourceSets" to sourceSets, "files" to indices)
    }

    private fun androidFiles(configurationName: String): List<File> {
        val configuration = project.configurations.getByName(configurationName)
        return (artifactView(configuration, "android-classes-jar").files + artifactView(configuration, "jar").files)
            .toList()
    }

    private fun artifactView(configuration: Configuration, artifactType: String): FileCollection =
        configuration.incoming.artifactView { view ->
            view.attributes { it.attribute(ARTIFACT_TYPE, artifactType) }
            view.isLenient = true
        }.files

    private companion object {
        val ARTIFACT_TYPE: Attribute<String> = Attribute.of("artifactType", String::class.java)
        const val ANDROID_PLATFORM = "androidJvm"
        val JVM_PLATFORMS = setOf("jvm", ANDROID_PLATFORM)
        val COMPONENT_CONFIGURATIONS = listOf(
            "main" to "CompileClasspath",
            "unitTest" to "UnitTestCompileClasspath",
            "androidTest" to "AndroidTestCompileClasspath",
        )
    }
}
