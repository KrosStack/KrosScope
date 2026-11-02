package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.RelationKind

private const val MAVEN_COORDINATES = "maven-coordinates"
private const val JVM_LIBRARY = "jvm-library"

private fun modules(name: String, producer: LinkProducer) = LinkClass(name, producer, GoldEnds::inModule)

private fun places(name: String, producer: LinkProducer) = LinkClass(name, producer, GoldEnds::inFile)

/** The relationship classes of the workspace of repositories that a scan can be scored on, by name. */
internal object LinkClasses {
    val all: Map<String, LinkClass> = listOf(
        modules("coordinate_dependency_to_module", ModuleDependencyLinks(MAVEN_COORDINATES)),
        places("coordinate_reference_to_declaration", CrossRepositoryCalls(MAVEN_COORDINATES)),
        modules("jvm_library_file_to_module", ModuleDependencyLinks(JVM_LIBRARY)),
        places("jvm_library_reference_to_declaration", CrossRepositoryCalls(JVM_LIBRARY)),
        places("native_library_load_to_target", SymbolLinks(RelationKind.BINDS, "native-library")),
        places("jni_external_to_native", NativeBindingLinks()),
        places("bundle_consumer_to_project", SymbolLinks(RelationKind.DEPENDS_ON, "web-bundle")),
        places("bundle_file_to_source", SymbolLinks(RelationKind.DEPENDS_ON, "source-map", withinRepository = true)),
        places("spec_input_to_spec", SymbolLinks(RelationKind.DEPENDS_ON, "generator-input")),
        places("generated_surface_to_spec", GeneratedSurfaceLinks()),
        places("spec_operation_to_implementation", SymbolLinks(RelationKind.BINDS, "openapi-route")),
    ).associateBy { it.name }
}
