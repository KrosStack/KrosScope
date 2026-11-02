package sample.tasktide.data.settings

import kotlinx.coroutines.flow.Flow
import sample.tasktide.core.model.Priority

data class AppPreferences(
    val syncOnWifiOnly: Boolean = true,
    val showCompleted: Boolean = true,
    val defaultPriority: Priority = Priority.NORMAL,
)

interface PreferenceRepository {
    fun observe(): Flow<AppPreferences>

    suspend fun setSyncOnWifiOnly(enabled: Boolean)

    suspend fun setShowCompleted(show: Boolean)

    suspend fun setDefaultPriority(priority: Priority)
}
