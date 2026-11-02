package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.analyzer.android.AndroidCapabilities
import io.github.krosstack.kscope.analyzer.android.aidl.AidlCapabilities
import io.github.krosstack.kscope.analyzer.compose.ComposeCapabilities
import io.github.krosstack.kscope.analyzer.history.HistoryCapabilities
import io.github.krosstack.kscope.analyzer.jni.JniCapabilities
import io.github.krosstack.kscope.analyzer.schema.SchemaCapabilities
import io.github.krosstack.kscope.analyzer.schema.web.WebBundleCapabilities
import io.github.krosstack.kscope.service.settings.AnalyzerKind
import io.github.krosstack.kscope.service.settings.AnalyzerSettings

/** Every switchable group of analyzers, each built when it is asked for. */
internal object AnalyzerGroups {
    fun of(selection: AnalyzerSettings): List<AnalyzerGroup> =
        AnalyzerKind.entries.filter(selection::isEnabled).map(::group)

    private fun group(kind: AnalyzerKind): AnalyzerGroup = when (kind) {
        AnalyzerKind.ANDROID_RESOURCES -> AnalyzerGroup(
            kind,
            detectors = AndroidCapabilities.detectors(),
            synthesizers = AndroidCapabilities.synthesizers(),
            producers = AndroidCapabilities.producers(),
            linkers = AndroidCapabilities.linkers(),
        )

        AnalyzerKind.AIDL -> AnalyzerGroup(
            kind,
            detectors = AidlCapabilities.detectors(),
            synthesizers = AidlCapabilities.synthesizers(),
            producers = AidlCapabilities.producers(),
            linkers = AidlCapabilities.linkers(),
        )

        AnalyzerKind.SCHEMAS -> AnalyzerGroup(
            kind,
            detectors = SchemaCapabilities.detectors(),
            synthesizers = SchemaCapabilities.synthesizers(),
            producers = SchemaCapabilities.producers(),
            linkers = SchemaCapabilities.linkers(),
        )

        AnalyzerKind.NATIVE_BINDINGS -> AnalyzerGroup(
            kind,
            detectors = JniCapabilities.detectors(),
            producers = JniCapabilities.producers(),
            linkers = JniCapabilities.linkers(),
        )

        AnalyzerKind.GIT_HISTORY -> AnalyzerGroup(kind, producers = HistoryCapabilities.producers())

        AnalyzerKind.COMPOSE_RESOURCES -> AnalyzerGroup(
            kind,
            detectors = ComposeCapabilities.detectors(),
            synthesizers = ComposeCapabilities.synthesizers(),
            producers = ComposeCapabilities.producers(),
        )

        AnalyzerKind.WEB_BUNDLES -> webBundles(kind)
    }

    private fun webBundles(kind: AnalyzerKind) = AnalyzerGroup(
        kind,
        detectors = WebBundleCapabilities.detectors(),
        producers = WebBundleCapabilities.producers(),
        linkers = WebBundleCapabilities.linkers(),
    )
}
