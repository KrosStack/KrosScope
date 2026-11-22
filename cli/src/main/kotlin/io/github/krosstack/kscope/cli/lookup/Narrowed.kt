package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.service.lookup.SearchOrigin

internal class Narrowed<out T>(val value: T, val origin: SearchOrigin = SearchOrigin())
