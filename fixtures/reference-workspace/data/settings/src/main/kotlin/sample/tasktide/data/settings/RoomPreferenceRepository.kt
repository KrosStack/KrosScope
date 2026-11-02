package sample.tasktide.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.currentTimeMillis

class RoomPreferenceRepository(
    private val dao: PreferenceDao,
    private val clock: () -> Long = { currentTimeMillis() },
) : PreferenceRepository {
    override fun observe(): Flow<AppPreferences> = dao.observeAll().map { rows ->
        val values = rows.associate { it.name to it.value }
        AppPreferences(
            syncOnWifiOnly = values[PreferenceKeys.SYNC_WIFI_ONLY]?.toBooleanStrictOrNull() ?: true,
            showCompleted = values[PreferenceKeys.SHOW_COMPLETED]?.toBooleanStrictOrNull() ?: true,
            defaultPriority = values[PreferenceKeys.DEFAULT_PRIORITY]?.let(::priorityOrNull) ?: Priority.NORMAL,
        )
    }

    override suspend fun setSyncOnWifiOnly(enabled: Boolean) = put(PreferenceKeys.SYNC_WIFI_ONLY, enabled.toString())

    override suspend fun setShowCompleted(show: Boolean) = put(PreferenceKeys.SHOW_COMPLETED, show.toString())

    override suspend fun setDefaultPriority(priority: Priority) = put(PreferenceKeys.DEFAULT_PRIORITY, priority.name)

    private suspend fun put(name: String, value: String) {
        dao.upsert(UserPreference(name = name, value = value, updatedAt = clock()))
    }

    private fun priorityOrNull(name: String): Priority? = Priority.entries.firstOrNull { it.name == name }
}
