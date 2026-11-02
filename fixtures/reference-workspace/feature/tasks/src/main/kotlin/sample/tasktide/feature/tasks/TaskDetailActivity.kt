package sample.tasktide.feature.tasks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sample.tasktide.core.legacy.LegacyTaskFormatter
import sample.tasktide.core.legacy.PriorityLabels
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.core.model.TaskStore
import sample.tasktide.feature.tasks.databinding.ActivityTaskDetailBinding

class TaskDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTaskDetailBinding
    private val formatter = LegacyTaskFormatter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.task_detail_title)

        val store = (application as TaskStoreProvider).taskStore
        val id = intent.getStringExtra(EXTRA_TASK_ID)?.let { TaskId(it) }
        lifecycleScope.launch {
            val task = id?.let { store.find(it) }
            if (task == null) binding.missing.root.isVisible = true else show(task, store)
        }
    }

    private fun show(task: Task, store: TaskStore) {
        binding.detailTitle.text = task.title
        binding.detailSummary.text = formatter.format(task)
        binding.detailPriority.text = getString(R.string.task_priority_format, PriorityLabels.labelFor(task.priority))
        binding.detailNotes.text = task.notes
        binding.toggleDone.setOnClickListener {
            lifecycleScope.launch {
                store.upsert(task.toggled())
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"

        fun intent(context: Context, id: TaskId): Intent =
            Intent(context, TaskDetailActivity::class.java).putExtra(EXTRA_TASK_ID, id.value)
    }
}
