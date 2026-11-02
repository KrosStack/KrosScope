package sample.tasktide.feature.tasks

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import sample.tasktide.core.model.TaskId
import sample.tasktide.feature.tasks.databinding.FragmentTaskListBinding
import sample.tasktide.feature.tasks.databinding.ViewEmptyStateBinding

class TaskListFragment : Fragment(R.layout.fragment_task_list) {
    private val viewModel by viewModels<TaskListViewModel> {
        TaskListViewModel.Factory((requireActivity().application as TaskStoreProvider).taskStore)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentTaskListBinding.bind(view)
        val adapter = TaskAdapter(onToggle = viewModel::toggleDone, onOpen = ::openDetail)
        binding.taskList.layoutManager = LinearLayoutManager(requireContext())
        binding.taskList.adapter = adapter
        requireActivity().title = getString(R.string.tasks_title)
        requireActivity().addMenuProvider(ListMenu(), viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.items)
                    showEmpty(binding.empty, state.items.isEmpty() && !state.loading)
                }
            }
        }
    }

    private fun showEmpty(empty: ViewEmptyStateBinding, visible: Boolean) {
        empty.root.isVisible = visible
    }

    private fun openDetail(id: TaskId) {
        val args = Bundle().apply { putString(TaskDetailActivity.EXTRA_TASK_ID, id.value) }
        findNavController().navigate(R.id.taskDetailActivity, args)
    }

    private fun shareExport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val csv = viewModel.exportCsv() ?: return@launch
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, csv)
            startActivity(Intent.createChooser(send, getString(R.string.action_export)))
        }
    }

    private inner class ListMenu : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_task_list, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.action_export -> true.also { shareExport() }
            R.id.action_delete_done -> true.also { viewModel.clearCompleted() }
            else -> false
        }
    }
}
