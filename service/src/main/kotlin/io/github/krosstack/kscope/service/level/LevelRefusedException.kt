package io.github.krosstack.kscope.service.level

/** A scan was not started because its level is not allowed; the index was not touched. */
public class LevelRefusedException(message: String) : IllegalStateException(message)
