package sample.tasktide.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sample.tasktide.app.databinding.ActivityMainBinding
import sample.tasktide.core.model.TaskId
import sample.tasktide.feature.settings.SettingsActivity
import sample.tasktide.feature.tasks.TaskDetailActivity
import sample.tasktide.sync.service.SyncStatus

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var syncClient: SyncServiceClient

    private val app: TaskTideApp
        get() = application as TaskTideApp

    private val creator by lazy { TaskCreator(app.taskStore, app.flags) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        syncClient = SyncServiceClient(applicationContext, ::showStatus)
        binding.syncButton.isEnabled = app.flags.syncEnabled
        binding.syncButton.setOnClickListener { syncClient.requestSync() }
        binding.addButton.setOnClickListener { addTask(binding.newTaskTitle.text.toString()) }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.nextUp.setContent { NextUpCard(app.taskStore) }

        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        syncClient.bind()
    }

    override fun onStop() {
        syncClient.unbind()
        super.onStop()
    }

    private fun handle(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.lastPathSegment?.let { startActivity(TaskDetailActivity.intent(this, TaskId(it))) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { addTask(it) }
        }
    }

    private fun addTask(title: String) {
        if (title.isBlank()) return
        lifecycleScope.launch {
            val priority = app.preferences.observe().first().defaultPriority
            when (val result = creator.create(title, priority)) {
                is CreateResult.Created -> binding.newTaskTitle.text.clear()
                is CreateResult.LimitReached ->
                    Toast.makeText(this@MainActivity, getString(R.string.limit_reached, result.max), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showStatus(status: SyncStatus) {
        binding.syncStatus.setText(
            when (status.state) {
                SyncStatus.STATE_RUNNING -> R.string.sync_status_running
                SyncStatus.STATE_FAILED -> R.string.sync_status_failed
                else -> R.string.sync_status_idle
            },
        )
    }
}
