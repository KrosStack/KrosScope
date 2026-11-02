package io.github.krosstack.kscope.model

import kotlinx.serialization.json.Json

public val KscopeJson: Json = Json {
    prettyPrint = false
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
    classDiscriminator = "type"
}
