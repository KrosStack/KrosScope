package sample.tasktide.server

import sample.tasktide.sync.v1.PushReply
import sample.tasktide.sync.v1.PushRequest

fun handlePush(request: PushRequest): PushReply = PushReply(accepted = request.task_ids.size)
