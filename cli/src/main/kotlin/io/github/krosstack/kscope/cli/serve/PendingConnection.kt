package io.github.krosstack.kscope.cli.serve

import java.net.Socket

internal class PendingConnection(val socket: Socket, val request: ServeRequest)
