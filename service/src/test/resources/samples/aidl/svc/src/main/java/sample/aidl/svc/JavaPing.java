package sample.aidl.svc;

public class JavaPing extends IPing.Stub {
    @Override
    public void ping(Payload payload) {
    }

    @Override
    public Payload latest() {
        return null;
    }

    @Override
    public void watch(IPongCallback callback) {
    }
}
