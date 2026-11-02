package sample.tasktide.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class SettingsActivity : ComponentActivity() {
    private val viewModel by viewModels<SettingsViewModel> {
        SettingsViewModel.factory((application as PreferenceRepositoryProvider).preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(viewModel)
            }
        }
    }
}
