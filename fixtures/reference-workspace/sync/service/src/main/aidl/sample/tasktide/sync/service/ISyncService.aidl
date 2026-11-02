package sample.tasktide.sync.service;

import sample.tasktide.sync.service.ISyncCallback;
import sample.tasktide.sync.service.SyncStatus;

interface ISyncService {
    void startSync();

    SyncStatus getStatus();

    void registerCallback(ISyncCallback callback);

    void unregisterCallback(ISyncCallback callback);
}
