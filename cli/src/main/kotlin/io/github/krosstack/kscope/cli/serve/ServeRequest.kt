package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.ClientRendering
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.Serializable

@Serializable
internal data class ServeRequest(
    val token: String = "",
    val arguments: List<String> = emptyList(),
    val directory: String = "",
    val rendering: ClientRendering = ClientRendering(),
    val action: String = "command",
    val toolVersion: String = KrosScope.toolVersion(),
    val protocol: Int = ServeWire.PROTOCOL,
)
