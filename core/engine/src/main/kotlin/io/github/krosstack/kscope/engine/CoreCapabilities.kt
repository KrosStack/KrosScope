package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.detector.GradleBuildScriptDetector
import io.github.krosstack.kscope.engine.detector.GradlePropertiesDetector
import io.github.krosstack.kscope.engine.detector.GradleSettingsDetector
import io.github.krosstack.kscope.engine.detector.GradleVersionCatalogDetector
import io.github.krosstack.kscope.engine.detector.JavaSourceDetector
import io.github.krosstack.kscope.engine.detector.KotlinScriptDetector
import io.github.krosstack.kscope.engine.detector.KotlinSourceDetector
import io.github.krosstack.kscope.engine.detector.MarkdownDetector
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BuildModelProvider

/** The generic capabilities that ship with the engine; analyzers contribute the rest. */
public object CoreCapabilities {
    public fun detectors(): List<ArtifactDetector> = listOf(
        KotlinSourceDetector(),
        KotlinScriptDetector(),
        JavaSourceDetector(),
        GradleSettingsDetector(),
        GradleBuildScriptDetector(),
        GradleVersionCatalogDetector(),
        GradlePropertiesDetector(),
        MarkdownDetector(),
    )

    public fun buildModelProviders(): List<BuildModelProvider> = listOf(StructuralBuildModelProvider())
}
