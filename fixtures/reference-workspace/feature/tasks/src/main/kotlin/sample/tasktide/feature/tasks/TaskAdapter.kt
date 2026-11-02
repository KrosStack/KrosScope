package sample.tasktide.feature.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import sample.tasktide.core.model.TaskId
import sample.tasktide.feature.tasks.databinding.ItemTaskBinding

class TaskAdapter(
    private val onToggle: (TaskId) -> Unit,
    private val onOpen: (TaskId) -> Unit,
) : ListAdapter<TaskItem, TaskAdapter.Holder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TaskItem) {
            val context = binding.root.context
            binding.title.text = item.title
            binding.title.setTextAppearance(if (item.done) R.style.TaskTitle_Done else R.style.TaskTitle)
            binding.subtitle.text = listOfNotNull(
                item.priorityLabel,
                item.dueDate?.let { context.getString(R.string.task_due_format, it) },
            ).joinToString(" · ")
            binding.priorityDot.priority = item.priority
            binding.done.setOnCheckedChangeListener(null)
            binding.done.isChecked = item.done
            binding.done.setOnCheckedChangeListener { _, _ -> onToggle(item.id) }
            binding.root.setOnClickListener { onOpen(item.id) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TaskItem>() {
        override fun areItemsTheSame(oldItem: TaskItem, newItem: TaskItem): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TaskItem, newItem: TaskItem): Boolean = oldItem == newItem
    }
}
