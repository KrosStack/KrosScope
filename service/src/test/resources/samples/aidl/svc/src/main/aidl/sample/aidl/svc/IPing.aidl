package sample.aidl.svc;

import sample.aidl.svc.IPongCallback;
import sample.aidl.svc.Payload;

interface IPing {
    const int MAX_PAYLOADS = 8;

    void ping(in Payload payload);

    Payload latest();

    oneway void watch(IPongCallback callback);
}
