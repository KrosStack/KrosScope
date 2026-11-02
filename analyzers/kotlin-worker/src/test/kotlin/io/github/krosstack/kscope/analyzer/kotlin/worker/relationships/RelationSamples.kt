package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerModule

/** The sample trees of the resolution pass and the module layouts in which the tests analyse them. */
object RelationSamples {
    private val ownStandardLibrary: String =
        checkNotNull(Unit::class.java.protectionDomain.codeSource).location.toURI().path.removePrefix("/")

    /** A classpath that only names the Kotlin standard library, enough to tell a module from one without any. */
    fun withClasspath(module: WorkerModule): WorkerModule = module.copy(classpath = listOf(ownStandardLibrary))

    private fun single(name: String, directory: String, platform: Platform = Platform.JVM) =
        SampleWorkspace.module(name, platform, SampleWorkspace.sourceSet(name, "main", "relations/$directory/main"))

    val lang: WorkerModule = single("lang", "lang")
    val mixed: WorkerModule = single("mixed", "mixed")
    val unresolved: WorkerModule = single("unresolved", "unresolved")
    val broken: WorkerModule = single("broken", "broken")
    val imports: WorkerModule = single("imports", "imports")
    val equality: WorkerModule = single("equality", "equality")
    val plugins: WorkerModule = single("plugins", "plugins")

    val common: WorkerModule = SampleWorkspace.module(
        "lib:commonMain",
        Platform.COMMON,
        SampleWorkspace.sourceSet("lib", "commonMain", "relations/kmp/commonMain"),
    )
    val jvm: WorkerModule = SampleWorkspace.module(
        "lib:jvmMain",
        Platform.JVM,
        SampleWorkspace.sourceSet("lib", "jvmMain", "relations/kmp/jvmMain"),
    ).copy(dependsOn = listOf("lib:commonMain"))
    val android: WorkerModule = SampleWorkspace.module(
        "lib:androidMain",
        Platform.ANDROID,
        SampleWorkspace.sourceSet("lib", "androidMain", "relations/kmp/androidMain"),
    ).copy(dependsOn = listOf("lib:commonMain"))
    val app: WorkerModule = SampleWorkspace.module(
        "app",
        Platform.JVM,
        SampleWorkspace.sourceSet("app", "main", "relations/kmp/app"),
    ).copy(dependencies = listOf("lib:jvmMain"))
    val multiplatform: List<WorkerModule> = listOf(common, jvm, android, app)

    val inheritLib: WorkerModule = SampleWorkspace.module(
        "inherit-lib",
        Platform.JVM,
        SampleWorkspace.sourceSet("inherit-lib", "main", "relations/inherit/lib"),
    )
    val inheritApp: WorkerModule = SampleWorkspace.module(
        "inherit-app",
        Platform.JVM,
        SampleWorkspace.sourceSet("inherit-app", "main", "relations/inherit/app"),
    ).copy(dependencies = listOf("inherit-lib"))
    val inheritance: List<WorkerModule> = listOf(inheritLib, inheritApp)

    val merged: WorkerModule = SampleWorkspace.module(
        "merged",
        Platform.ANDROID,
        SampleWorkspace.sourceSet("merged", "main", "relations/merged/main"),
        SampleWorkspace.sourceSet("merged", "debug", "relations/merged/debug"),
    )

    private val visibilityMain = SampleWorkspace.module(
        "api",
        Platform.JVM,
        SampleWorkspace.sourceSet("api", "main", "visibility/main"),
    )
    val friendTest: WorkerModule = SampleWorkspace.module(
        "api-test",
        Platform.JVM,
        SampleWorkspace.sourceSet("api-test", "test", "visibility/test"),
    ).copy(dependencies = listOf("api"), friends = listOf("api"))
    val strangerTest: WorkerModule = friendTest.copy(friends = emptyList())
    val friends: List<WorkerModule> = listOf(visibilityMain, friendTest)
    val strangers: List<WorkerModule> = listOf(visibilityMain, strangerTest)

    private fun stub(path: String, language: String, text: String) =
        SyntheticSource(path, language, text, SourceSetId("replaced-by-the-source-set"), "test.generator")

    val stubs: List<SyntheticSource> = listOf(
        stub(
            "rel/stubs/R",
            "java",
            "package rel.stubs;\npublic final class R {\n    public static final class string {\n" +
                "        public static final int app_name = 0;\n    }\n}\n",
        ),
        stub(
            "rel/stubs/BuildConfig",
            "java",
            "package rel.stubs;\npublic final class BuildConfig {\n    public static final boolean DEBUG = true;\n}\n",
        ),
        stub(
            "rel/stubs/IFoo",
            "java",
            "package rel.stubs;\npublic interface IFoo {\n    void ping();\n" +
                "    abstract class Stub implements IFoo {\n" +
                "        public static IFoo asInterface(Object binder) {\n" +
                "            return null;\n        }\n    }\n}\n",
        ),
        stub("rel/stubs/Generated", "kotlin", "package rel.stubs\n\nobject Generated {\n    val value: Int = 0\n}\n"),
    )

    private val stubTypes = listOf("R", "BuildConfig", "IFoo", "Generated")

    /** Whether [id] names a declaration of a synthetic stub, which a generator rather than the analyzer emits. */
    fun isStubSymbol(id: SymbolId): Boolean = stubTypes.any { id.value.contains(" rel/stubs/$it#") }

    val withStubs: WorkerModule = SampleWorkspace.module(
        "gen",
        Platform.ANDROID,
        SampleWorkspace.sourceSet("gen", "main", "relations/stubs/main", syntheticSources = stubs),
    )
}
