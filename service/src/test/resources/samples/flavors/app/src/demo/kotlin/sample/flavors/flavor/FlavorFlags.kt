package sample.flavors.flavor

import sample.flavors.config.FeatureFlags

object FlavorFlags : FeatureFlags {
    override val maxTasks: Int = 25
}
