package sample.tasktide.client

import sample.tasktide.api.apis.TasksApi

class TaskClient(private val api: TasksApi) {
    fun titles(): List<String> = api.listTasks().map { it.title }
}
