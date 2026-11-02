package sample.tasktide.client

import sample.tasktide.sync.v1.PushRequest

fun pushRequest(deviceId: String, taskIds: List<String>): PushRequest =
    PushRequest(device_id = deviceId, task_ids = taskIds)
