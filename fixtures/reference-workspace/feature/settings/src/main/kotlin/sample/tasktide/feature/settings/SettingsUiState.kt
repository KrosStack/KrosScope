package sample.tasktide.feature.settings

import sample.tasktide.core.model.Priority

data class SettingsUiState(
    val syncOnWifiOnly: Boolean = true,
    val showCompleted: Boolean = true,
    val defaultPriority: Priority = Priority.NORMAL,
)
