package sample.tasktide.app.flavor

import sample.tasktide.app.BuildConfig
import sample.tasktide.app.config.FeatureFlags

object FlavorFlags : FeatureFlags {
    override val syncEnabled: Boolean = false
    override val maxTasks: Int = BuildConfig.MAX_TASKS
}
