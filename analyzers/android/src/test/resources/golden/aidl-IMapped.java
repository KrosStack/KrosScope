package p;

public interface IMapped extends android.os.IInterface {
    public static final java.lang.String DESCRIPTOR = "p.IMapped";
    public static final long LIMIT = 0;
    public static final java.lang.String NAME = null;
    public static final boolean ENABLED = false;
    public java.lang.String a(java.lang.CharSequence b, java.util.List<java.lang.String> c, java.util.Map<java.lang.String, q.Foo> d) throws android.os.RemoteException;
    public q.Foo[] b(android.os.Bundle e, int[] f, long g, android.os.IBinder h, android.os.ParcelFileDescriptor i) throws android.os.RemoteException;
    public java.util.List<q.Foo> c(java.io.FileDescriptor j, p.Bar k, q.Baz l, boolean m, char n) throws android.os.RemoteException;
    public byte[] d() throws android.os.RemoteException;
    public static class Default implements p.IMapped {
    }
    public static abstract class Stub extends android.os.Binder implements p.IMapped {
        public static p.IMapped asInterface(android.os.IBinder obj) { return null; }
        public android.os.IBinder asBinder() { return null; }
    }
}
