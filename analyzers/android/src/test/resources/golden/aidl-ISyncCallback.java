package sample.tasktide.sync.service;

public interface ISyncCallback extends android.os.IInterface {
    public static final java.lang.String DESCRIPTOR = "sample.tasktide.sync.service.ISyncCallback";
    public void onSyncStarted() throws android.os.RemoteException;
    public void onSyncFinished(sample.tasktide.sync.service.SyncStatus status) throws android.os.RemoteException;
    public void onSyncFailed(java.lang.String reason) throws android.os.RemoteException;
    public static class Default implements sample.tasktide.sync.service.ISyncCallback {
    }
    public static abstract class Stub extends android.os.Binder implements sample.tasktide.sync.service.ISyncCallback {
        public static sample.tasktide.sync.service.ISyncCallback asInterface(android.os.IBinder obj) { return null; }
        public android.os.IBinder asBinder() { return null; }
    }
}
