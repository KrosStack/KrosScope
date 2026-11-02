package sample.tasktide.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sample.tasktide.core.model.Priority
import sample.tasktide.data.settings.PreferenceRepository

class SettingsViewModel(private val repository: PreferenceRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState> = repository.observe()
        .map { SettingsUiState(it.syncOnWifiOnly, it.showCompleted, it.defaultPriority) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onSyncWifiOnlyChanged(enabled: Boolean) {
        viewModelScope.launch { repository.setSyncOnWifiOnly(enabled) }
    }

    fun onShowCompletedChanged(show: Boolean) {
        viewModelScope.launch { repository.setShowCompleted(show) }
    }

    fun onDefaultPriorityChanged(priority: Priority) {
        viewModelScope.launch { repository.setDefaultPriority(priority) }
    }

    companion object {
        fun factory(repository: PreferenceRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
