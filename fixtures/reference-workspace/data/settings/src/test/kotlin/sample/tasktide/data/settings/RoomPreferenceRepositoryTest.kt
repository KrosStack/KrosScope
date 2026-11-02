package sample.tasktide.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sample.tasktide.core.model.Priority
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomPreferenceRepositoryTest {
    private val dao = InMemoryPreferenceDao()
    private val repository = RoomPreferenceRepository(dao, clock = { 123L })

    @Test
    fun defaultsApplyWhenNothingIsStored() = runTest {
        assertEquals(AppPreferences(), repository.observe().first())
    }

    @Test
    fun storedValuesOverrideDefaults() = runTest {
        repository.setSyncOnWifiOnly(false)
        repository.setDefaultPriority(Priority.HIGH)

        val preferences = repository.observe().first()

        assertFalse(preferences.syncOnWifiOnly)
        assertTrue(preferences.showCompleted)
        assertEquals(Priority.HIGH, preferences.defaultPriority)
    }

    @Test
    fun writesCarryTheClockTimestamp() = runTest {
        repository.setShowCompleted(false)

        assertEquals(123L, dao.find(PreferenceKeys.SHOW_COMPLETED)?.updatedAt)
    }

    private class InMemoryPreferenceDao : PreferenceDao {
        private val rows = MutableStateFlow<Map<String, UserPreference>>(emptyMap())

        override fun observeAll(): Flow<List<UserPreference>> = rows.map { it.values.sortedBy(UserPreference::name) }

        override suspend fun find(name: String): UserPreference? = rows.value[name]

        override suspend fun upsert(preference: UserPreference) {
            rows.value = rows.value + (preference.name to preference)
        }

        override suspend fun delete(name: String) {
            rows.value = rows.value - name
        }
    }
}
