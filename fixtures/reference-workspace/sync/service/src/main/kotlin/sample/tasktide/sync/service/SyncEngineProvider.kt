package sample.tasktide.sync.service

import sample.tasktide.sync.engine.SyncEngine

interface SyncEngineProvider {
    val syncEngine: SyncEngine
}
