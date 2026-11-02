package sample.tasktide.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import sample.tasktide.core.model.Priority
import sample.tasktide.data.settings.AppPreferences
import sample.tasktide.data.settings.PreferenceRepository

class FakePreferenceRepository : PreferenceRepository {
    private val preferences = MutableStateFlow(AppPreferences())

    override fun observe(): Flow<AppPreferences> = preferences

    override suspend fun setSyncOnWifiOnly(enabled: Boolean) = preferences.update { it.copy(syncOnWifiOnly = enabled) }

    override suspend fun setShowCompleted(show: Boolean) = preferences.update { it.copy(showCompleted = show) }

    override suspend fun setDefaultPriority(priority: Priority) =
        preferences.update { it.copy(defaultPriority = priority) }
}
