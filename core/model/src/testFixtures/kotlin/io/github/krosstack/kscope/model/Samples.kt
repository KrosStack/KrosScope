package io.github.krosstack.kscope.model

public object Samples {
    public val repositoryId = RepositoryId("repo-1")
    public val buildRootId = BuildRootId("repo-1//")
    public val moduleId = ModuleId("repo-1//:core:model")
    public val sourceSetId = SourceSetId("repo-1//:core:model@commonMain")
    public val targetId = TargetId("repo-1//:core:model#target/jvm")
    public val variantId = VariantId("repo-1//:app#variant/debug")
    public val contentHash = ContentHash("a".repeat(64))
    public val fingerprint = Fingerprint("f".repeat(64))

    public val artifactRef = ArtifactRef(repositoryId, RepoPath("core/model/src/commonMain/kotlin/Model.kt"))
    public val range = TextRange(startLine = 3, startColumn = 1, endLine = 9, endColumn = 2)
    public val location = SourceLocation(artifactRef, range)
    public val scope = Scope(sourceSet = sourceSetId, target = targetId, variant = variantId)

    public val symbolId = SymbolIdentity.workspace(
        module = moduleId,
        sourceSet = "commonMain",
        descriptor = SymbolDescriptor().namespace(
            "com",
        ).namespace("example").type("Model").method("load", "String,Int"),
    ).toSymbolId()

    public val nodeRefs: List<NodeRef> = listOf(
        NodeRef.OfSymbol(symbolId),
        NodeRef.OfArtifact(artifactRef),
        NodeRef.OfModule(moduleId),
        NodeRef.OfSourceSet(sourceSetId),
        NodeRef.OfExternal("maven:org.example:lib"),
    )

    public val resolutions: List<Resolution> = listOf(
        Resolution.Resolved(NodeRef.OfSymbol(symbolId), Certainty.EXACT),
        Resolution.Unresolved("Missing", ReasonCode("kotlin.unresolved-reference")),
        Resolution.NotAnalysed("Native", ReasonCode("jni.native-side-not-analysed")),
    )

    public val origins: List<SymbolOrigin> = listOf(
        SymbolOrigin.Source,
        SymbolOrigin.Generated("aidl", NodeRef.OfArtifact(artifactRef)),
        SymbolOrigin.Synthetic("serialization"),
        SymbolOrigin.Library("org.example:lib"),
    )

    public val repository = Repository(repositoryId, "sample", headRevision = "abc123", originUrl = null)
    public val buildRoot = BuildRoot(buildRootId, repositoryId, RepoPath(""), BuildSystem.GRADLE)

    public val module = Module(
        id = moduleId,
        buildRoot = buildRootId,
        path = ":core:model",
        directory = RepoPath("core/model"),
        plugins = listOf("org.jetbrains.kotlin.multiplatform", "com.android.library"),
        traits = setOf(ModuleTrait.KOTLIN_MULTIPLATFORM, ModuleTrait.ANDROID_LIBRARY),
        androidNamespace = "org.example.model",
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.PROBABLE,
        attributes = mapOf("android.compileSdk" to "36", "android.buildFeatures.compose" to "true"),
    )

    public val target = Target(targetId, moduleId, "jvm", Platform.JVM)
    public val variant = Variant(variantId, moduleId, "debug", buildType = "debug", flavors = listOf("free"))

    public val sourceSet = SourceSet(
        id = sourceSetId,
        module = moduleId,
        name = "commonMain",
        role = SourceSetRole.MAIN,
        dependsOn = emptyList(),
        roots = listOf(SourceRoot(RepoPath("core/model/src/commonMain/kotlin"), SourceRootKind("kotlin"), false)),
        targets = listOf(targetId),
        variants = listOf(variantId),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    public val artifact = Artifact(
        ref = artifactRef,
        type = ArtifactType("kotlin.source", ArtifactCategory.SOURCE),
        contentHash = contentHash,
        sizeBytes = 2048,
        lineCount = 80,
        sourceSet = sourceSetId,
        generated = GeneratedInfo("aidl", NodeRef.OfArtifact(artifactRef)),
    )

    public val symbol = Symbol(
        id = symbolId,
        kind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE),
        name = "load",
        qualifiedName = "com.example.Model.load",
        signature = "fun load(name: String, count: Int): Model",
        container = null,
        visibility = Visibility.PUBLIC,
        flags = setOf(SymbolFlag.SUSPEND, SymbolFlag.OPEN),
        origin = SymbolOrigin.Source,
        declaration = location,
        nameLocation = location,
        documentation = "Loads a model.",
        scope = scope,
        evidenceClass = EvidenceClass.STRUCTURAL,
        certainty = Certainty.EXACT,
        attributes = mapOf("receiver" to "none", "arity" to "2"),
    )

    public val relationship = Relationship(
        from = NodeRef.OfSymbol(symbolId),
        kind = RelationKind.CALLS,
        subKind = "kotlin.invoke",
        resolution = resolutions.first(),
        evidenceClass = EvidenceClass.COMPILER,
        site = location,
        scope = scope,
        attributes = mapOf("z" to "1", "a" to "2"),
    )

    public val symbolicReference = SymbolicReference(
        from = NodeRef.OfArtifact(artifactRef),
        kind = RelationKind.REFERENCES,
        subKind = "style-parent",
        namespace = "android.resource",
        key = "string/app_name",
        site = location,
        scope = scope,
        evidenceClass = EvidenceClass.TEXTUAL,
    )

    public val metric = Metric(NodeRef.OfArtifact(artifactRef), HistorySignals.COMMITS, 12.0)

    public val diagnostic = Diagnostic(
        severity = DiagnosticSeverity.WARNING,
        code = ReasonCode("identity.collision"),
        message = "Two overloads share a descriptor",
        location = location,
    )

    public val facts: List<Fact> = listOf(
        Fact.OfRepository(repository),
        Fact.OfBuildRoot(buildRoot),
        Fact.OfModule(module),
        Fact.OfTarget(target),
        Fact.OfVariant(variant),
        Fact.OfSourceSet(sourceSet),
        Fact.OfArtifact(artifact),
        Fact.OfSymbol(symbol),
        Fact.OfRelationship(relationship),
        Fact.OfSymbolicReference(symbolicReference),
        Fact.OfMetric(metric),
        Fact.OfDiagnostic(diagnostic),
    )

    public val producer = ProducerRef("kotlin-structure", "1.0.0")
    public val owner = OwnershipUnit(OwnershipKind.ARTIFACT, "repo-1:core/model/src/commonMain/kotlin/Model.kt")
    public val batch = FactBatch(producer, owner, fingerprint, facts)
}
