package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.wiring.toService
import io.github.krosstack.kscope.engine.progress.ScanStages as EngineStages

/** The stages of a scan, in the order they run. Their ids are also the keys of the stage timings of a scan result. */
public object ScanStages {
    public val DISCOVERY: ProgressStage = EngineStages.DISCOVERY.toService()
    public val BUILD_MODEL: ProgressStage = EngineStages.BUILD_MODEL.toService()
    public val SOURCE_SETS: ProgressStage = EngineStages.SOURCE_SETS.toService()
    public val SYNTHESIS: ProgressStage = EngineStages.SYNTHESIS.toService()
    public val PRODUCTION: ProgressStage = EngineStages.PRODUCTION.toService()
    public val LINKING: ProgressStage = EngineStages.LINKING.toService()
    public val CLEANUP: ProgressStage = EngineStages.CLEANUP.toService()
    public val SEARCH_INDEX: ProgressStage = EngineStages.SEARCH_INDEX.toService()

    public const val VERIFY_PREFIX: String = EngineStages.VERIFY_PREFIX
}
