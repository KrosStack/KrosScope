package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.model.FactBatch
import kotlin.test.Test
import kotlin.test.assertTrue

class MultiplatformRelationshipsTest {
    private val batches: List<FactBatch> by lazy { analyzeAll(RelationSamples.multiplatform) }
    private val edges: List<String> by lazy { Edges().of(batches) }
    private val common = "lib@commonMain rel/kmp/"
    private val jvm = "lib@jvmMain rel/kmp/"
    private val android = "lib@androidMain rel/kmp/"

    @Test
    fun `every actual declaration actualizes the expect declaration of the common source set`() {
        Edges.assertHas(
            edges,
            "${jvm}PlatformInfo# actualizes -> ${common}PlatformInfo#",
            "${jvm}PlatformInfo#name. actualizes -> ${common}PlatformInfo#name.",
            "${jvm}PlatformInfo#describe(). actualizes -> ${common}PlatformInfo#describe().",
            "${jvm}currentPlatform(). actualizes -> ${common}currentPlatform().",
            "${jvm}Clock# actualizes -> ${common}Clock#",
            "${jvm}Clock#now(). actualizes -> ${common}Clock#now().",
            "${jvm}Counter#`<init>`(Int). actualizes -> ${common}Counter#`<init>`(Int).",
        )
    }

    @Test
    fun `one expect declaration is actualized from each platform source set`() {
        Edges.assertHas(
            edges,
            "${jvm}currentPlatform(). actualizes -> ${common}currentPlatform().",
            "${android}currentPlatform(). actualizes -> ${common}currentPlatform().",
            "${android}Counter#next(). actualizes -> ${common}Counter#next().",
            "${android}Clock# actualizes -> ${common}Clock#",
        )
    }

    @Test
    fun `actualizes edges lead from a platform source set to the common one and from nowhere else`() {
        val actualizations = edges.filter { " actualizes" in it }
        val platforms = listOf("lib@jvmMain ", "lib@androidMain ")
        assertTrue(actualizations.isNotEmpty())
        assertTrue(actualizations.all { edge -> platforms.any { edge.startsWith(it) } })
        assertTrue(actualizations.all { it.substringAfter("-> ").startsWith(common) })
    }

    @Test
    fun `common code refers to the expect declarations and platform code to the actual ones`() {
        Edges.assertHas(
            edges,
            "${common}describeCurrent(). calls -> ${common}currentPlatform().",
            "${common}describeCurrent(). calls -> ${common}Clock#now().",
            "${common}describeCurrent(). calls -> ${common}PlatformInfo#`<init>`().",
            "${jvm}jvmDescription(). calls -> ${jvm}currentPlatform().",
            "${jvm}jvmDescription(). calls -> ${jvm}Clock#now().",
            "${jvm}jvmDescription(). calls -> ${common}describeCurrent().",
        )
    }

    @Test
    fun `platform code that extends and overrides common code refers to the common declarations`() {
        Edges.assertHas(
            edges,
            "${jvm}LoudGreeter# extends -> ${common}BaseGreeter#",
            "${jvm}LoudGreeter# calls -> ${common}BaseGreeter#`<init>`(String).",
            "${jvm}LoudGreeter#greet(String). overrides -> ${common}BaseGreeter#greet(String).",
            "${jvm}LoudGreeter#greet(String). calls -> ${common}BaseGreeter#greet(String).",
            "${common}BaseGreeter# implements -> ${common}Greeter#",
        )
    }

    @Test
    fun `a module that depends on a platform source set refers to the declarations of its refinement closure`() {
        Edges.assertHas(
            edges,
            "file:relations/kmp/app/kotlin/rel/app/Main.kt imports -> ${jvm}LoudGreeter#",
            "file:relations/kmp/app/kotlin/rel/app/Main.kt imports -> ${common}greetAll(Greeter,List).",
            "app@main rel/app/main(). calls/constructor -> ${jvm}LoudGreeter#",
            "app@main rel/app/main(). calls -> ${common}greetAll(Greeter,List).",
            "app@main rel/app/main(). calls -> ${jvm}Clock#now().",
            "app@main rel/app/main(). calls -> ${jvm}Counter#next().",
        )
    }

    @Test
    fun `two source sets of one module refer to each other and name the targets by their own source set`() {
        val merged = Edges("merged@").of(analyzeAll(RelationSamples.merged))
        Edges.assertHas(
            merged,
            "main rel/merged/MainScreen#render(). calls/constructor -> debug rel/merged/DebugOverlay#",
            "main rel/merged/MainScreen#render(). calls -> debug rel/merged/DebugOverlay#label().",
            "main rel/merged/MainScreen#render(). calls -> main rel/merged/helperInMain().",
            "debug rel/merged/DebugOverlay#show(MainScreen). calls -> main rel/merged/MainScreen#render().",
            "debug rel/merged/debugOnly(). calls -> main rel/merged/mainOnly().",
            "main rel/merged/mainOnly(). calls -> debug rel/merged/debugOnly().",
        )
    }

    @Test
    fun `a friend module reaches the internal declarations of its target exactly`() {
        val friend = Edges().of(analyzeAll(RelationSamples.friends))
        Edges.assertHas(
            friend,
            "api-test@test vis/useApis(). calls -> api@main vis/publicApi().",
            "api-test@test vis/useApis(). calls -> api@main vis/internalApi().",
        )
    }

    @Test
    fun `a module that is not a friend reaches an internal declaration only as a probable target`() {
        val stranger = Edges().of(analyzeAll(RelationSamples.strangers))
        Edges.assertHas(
            stranger,
            "api-test@test vis/useApis(). calls -> api@main vis/publicApi().",
            "api-test@test vis/useApis(). calls -> api@main vis/internalApi(). ~probable",
        )
    }
}
