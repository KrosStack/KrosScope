package sample.tasktide.app.config

interface FeatureFlags {
    val syncEnabled: Boolean
    val maxTasks: Int
}
