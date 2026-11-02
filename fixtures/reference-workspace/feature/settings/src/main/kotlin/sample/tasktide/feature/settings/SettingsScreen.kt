package sample.tasktide.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sample.tasktide.core.model.Priority
import sample.tasktide.ui.shared.PriorityBadge

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        onSyncWifiOnlyChange = viewModel::onSyncWifiOnlyChanged,
        onShowCompletedChange = viewModel::onShowCompletedChanged,
        onDefaultPriorityChange = viewModel::onDefaultPriorityChanged,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onSyncWifiOnlyChange: (Boolean) -> Unit,
    onShowCompletedChange: (Boolean) -> Unit,
    onDefaultPriorityChange: (Priority) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        SwitchRow(stringResource(R.string.settings_sync_wifi_only), state.syncOnWifiOnly, onSyncWifiOnlyChange)
        SwitchRow(stringResource(R.string.settings_show_completed), state.showCompleted, onShowCompletedChange)
        Text(stringResource(R.string.settings_default_priority))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { priority ->
                PriorityOption(priority, selected = priority == state.defaultPriority) {
                    onDefaultPriorityChange(priority)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PriorityOption(priority: Priority, selected: Boolean, onSelect: () -> Unit) {
    PriorityBadge(
        priority = priority,
        modifier = Modifier
            .alpha(if (selected) 1f else 0.4f)
            .selectable(selected = selected, onClick = onSelect),
    )
}
