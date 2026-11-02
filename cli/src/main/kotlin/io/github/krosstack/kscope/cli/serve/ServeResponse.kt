package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.Serializable

@Serializable
internal data class ServeResponse(
    val kind: String,
    val stream: String = "",
    val text: String = "",
    val exitCode: Int = 0,
    val toolVersion: String = KrosScope.toolVersion(),
    val protocol: Int = ServeWire.PROTOCOL,
)
