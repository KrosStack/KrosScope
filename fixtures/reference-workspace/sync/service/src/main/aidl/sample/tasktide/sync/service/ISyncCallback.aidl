package sample.tasktide.sync.service;

import sample.tasktide.sync.service.SyncStatus;

oneway interface ISyncCallback {
    void onSyncStarted();

    void onSyncFinished(in SyncStatus status);

    void onSyncFailed(String reason);
}
