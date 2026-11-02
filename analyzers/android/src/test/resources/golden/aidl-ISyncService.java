package sample.tasktide.sync.service;

public interface ISyncService extends android.os.IInterface {
    public static final java.lang.String DESCRIPTOR = "sample.tasktide.sync.service.ISyncService";
    public void startSync() throws android.os.RemoteException;
    public sample.tasktide.sync.service.SyncStatus getStatus() throws android.os.RemoteException;
    public void registerCallback(sample.tasktide.sync.service.ISyncCallback callback) throws android.os.RemoteException;
    public void unregisterCallback(sample.tasktide.sync.service.ISyncCallback callback) throws android.os.RemoteException;
    public static class Default implements sample.tasktide.sync.service.ISyncService {
    }
    public static abstract class Stub extends android.os.Binder implements sample.tasktide.sync.service.ISyncService {
        public static sample.tasktide.sync.service.ISyncService asInterface(android.os.IBinder obj) { return null; }
        public android.os.IBinder asBinder() { return null; }
    }
}
