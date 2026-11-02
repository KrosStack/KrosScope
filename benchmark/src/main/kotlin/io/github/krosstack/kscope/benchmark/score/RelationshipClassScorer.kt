package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.gold.GoldRelationships
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.build.CatalogAliasScorer
import io.github.krosstack.kscope.benchmark.score.build.ConventionPluginScorer
import io.github.krosstack.kscope.benchmark.score.code.ExpectActualScorer
import io.github.krosstack.kscope.benchmark.score.code.FlavorImplementationScorer
import io.github.krosstack.kscope.benchmark.score.code.TestSubjectScorer
import io.github.krosstack.kscope.benchmark.score.contract.AidlScorer
import io.github.krosstack.kscope.benchmark.score.contract.JniExternalScorer
import io.github.krosstack.kscope.benchmark.score.contract.RoomConsumerScorer
import io.github.krosstack.kscope.benchmark.score.contract.RoomStructureScorer
import io.github.krosstack.kscope.benchmark.score.contract.SqlQueryScorer
import io.github.krosstack.kscope.benchmark.score.contract.protoScorer
import io.github.krosstack.kscope.benchmark.score.contract.sqlTableScorer
import io.github.krosstack.kscope.benchmark.score.resource.BuildConfigScorer
import io.github.krosstack.kscope.benchmark.score.resource.ComposeResourceScorer
import io.github.krosstack.kscope.benchmark.score.resource.LayoutClassReferenceScorer
import io.github.krosstack.kscope.benchmark.score.resource.ManifestComponentScorer
import io.github.krosstack.kscope.benchmark.score.resource.ResourceToConsumerScorer
import io.github.krosstack.kscope.benchmark.score.resource.ViewBindingScorer

/**
 * Scores the relationship classes that the analyzers make at this phase: `expect_actual`, `kotlin_calls_java`,
 * `java_calls_kotlin`, `catalog_alias_to_module`, `convention_plugin_to_module`, `resource_to_consumer`,
 * `manifest_component_to_class`, `layout_class_reference`, `viewbinding_layout_to_consumer`,
 * `buildconfig_field_to_consumer`, the four AIDL classes, `jni_external_to_native`, the schema classes,
 * `compose_resource_to_consumer`, `flavor_specific_implementation` and `test_to_subject`. The other classes are listed
 * with their gold count and no score, because their producers do not exist yet.
 */
internal class RelationshipClassScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val aidl = AidlScorer(index, resolver)
    private val scorers: Map<String, (List<GoldRelationship>) -> ClassScore> = mapOf(
        "expect_actual" to ExpectActualScorer(index, resolver)::score,
        "kotlin_calls_java" to { gold -> kotlinCallsJava.score(index, resolver, gold) },
        "java_calls_kotlin" to { gold -> javaCallsKotlin.score(index, resolver, gold) },
        "catalog_alias_to_module" to CatalogAliasScorer(index, resolver)::score,
        "convention_plugin_to_module" to ConventionPluginScorer(index, resolver)::score,
        "resource_to_consumer" to ResourceToConsumerScorer(index, resolver)::score,
        "manifest_component_to_class" to ManifestComponentScorer(index, resolver)::score,
        "layout_class_reference" to LayoutClassReferenceScorer(index, resolver)::score,
        "viewbinding_layout_to_consumer" to ViewBindingScorer(index, resolver)::score,
        "buildconfig_field_to_consumer" to BuildConfigScorer(index, resolver)::score,
        "aidl_interface_to_implementer" to aidl::implementers,
        "aidl_interface_to_client" to aidl::clients,
        "aidl_method_to_override" to aidl::overrides,
        "aidl_parcelable_to_class" to aidl::parcelables,
        "jni_external_to_native" to JniExternalScorer(index, resolver)::score,
        "proto_message_to_consumer" to protoScorer(index, resolver)::score,
        "sq_query_to_consumer" to SqlQueryScorer(index, resolver)::score,
        "sq_table_to_consumer" to sqlTableScorer(index, resolver)::score,
        "room_entity_dao_database" to RoomStructureScorer(index, resolver)::score,
        "room_component_to_consumer" to RoomConsumerScorer(index, resolver)::score,
        "compose_resource_to_consumer" to ComposeResourceScorer(index, resolver)::score,
        "flavor_specific_implementation" to FlavorImplementationScorer(index, resolver)::score,
        "test_to_subject" to TestSubjectScorer(index, resolver)::score,
    )

    fun score(gold: GoldRelationships): List<ClassScore> =
        gold.relationships.groupBy { it.relationshipClass }.toSortedMap().map { (name, relationships) ->
            scorers[name]?.invoke(relationships) ?: ClassScore(name, false, relationships.size, null, 0, emptyList())
        }
}
