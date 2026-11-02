package io.github.krosstack.kscope.cli.serve

import java.io.IOException

internal class ServerAlreadyRunning : IOException("A server already runs for this workspace.")
