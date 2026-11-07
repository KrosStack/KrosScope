package io.github.krosstack.kscope.service.components

public enum class ComponentState(public val text: String) {
    INSTALLED("installed"),
    SELF_TEST_FAILED("installed; self-test failed"),
    CORRUPT("corrupt installation"),
    NOT_INSTALLED("not installed"),
    DISABLED("disabled by workspace"),
    OTHER_VERSION("other version installed"),
    UNSUPPORTED("unsupported platform"),
}
