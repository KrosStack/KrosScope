package sample.tasktide.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import sample.tasktide.core.model.Priority
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository = FakePreferenceRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateStartsWithDefaults() {
        val viewModel = SettingsViewModel(repository)

        assertEquals(SettingsUiState(), viewModel.state.value)
    }

    @Test
    fun wifiOnlyToggleIsPersistedAndReflected() {
        val viewModel = SettingsViewModel(repository)

        viewModel.onSyncWifiOnlyChanged(false)

        assertFalse(viewModel.state.value.syncOnWifiOnly)
    }

    @Test
    fun defaultPriorityChangeIsReflected() {
        val viewModel = SettingsViewModel(repository)

        viewModel.onDefaultPriorityChanged(Priority.URGENT)

        assertEquals(Priority.URGENT, viewModel.state.value.defaultPriority)
    }
}
