package io.github.krosstack.kscope.cli.serve.watch

internal enum class WatchReason(val text: String) {
    SETTLING("changes are settling"),
    GIT("a Git operation is in progress"),
    TRUSTED("the index was built at level exact and is not updated automatically"),
    DEEPENING("the index is shallower than wanted and is being deepened"),
    DEEPENING_RETRY("the scan did not reach the wanted level, for instance because the build failed, and is retried"),
    DEEPENING_STOPPED(
        "the scan did not reach the wanted level, for instance because the build failed; it waits for a change",
    ),
    RETRY("an update failed and is retried"),
    SETTINGS("kscope.toml is not valid and updates wait until it is fixed"),
    OFF("watching is turned off in kscope.toml"),
    STOPPED("the watcher gave up after repeated failures"),
}
