package io.github.krosstack.kscope.analyzer.android.aidl

/** The `.aidl` files of the tests, as they are written in a project. */
internal object AidlSamples {
    val syncCallback = """
        package sample.tasktide.sync.service;

        import sample.tasktide.sync.service.SyncStatus;

        oneway interface ISyncCallback {
            void onSyncStarted();

            void onSyncFinished(in SyncStatus status);

            void onSyncFailed(String reason);
        }
    """.trimIndent() + "\n"

    val syncService = """
        package sample.tasktide.sync.service;

        import sample.tasktide.sync.service.ISyncCallback;
        import sample.tasktide.sync.service.SyncStatus;

        interface ISyncService {
            void startSync();

            SyncStatus getStatus();

            void registerCallback(ISyncCallback callback);

            void unregisterCallback(ISyncCallback callback);
        }
    """.trimIndent() + "\n"

    val syncStatus = """
        package sample.tasktide.sync.service;

        parcelable SyncStatus;
    """.trimIndent() + "\n"

    val report = """
        package sample.tasktide.sync.service;

        import android.os.Bundle;

        /** A structured parcelable. */
        @JavaDerive(toString = true)
        parcelable Report {
            const int STATE_OPEN = 1;
            int count;
            String title = "none";
            List<String> tags;
            Bundle extras;
            sample.tasktide.sync.service.SyncStatus[] history;
        }
    """.trimIndent() + "\n"

    val everything = """
        // Header comment
        package sample.aidl.svc; /* trailing */

        import android.os.Bundle;
        import android.os.ParcelFileDescriptor;
        import sample.aidl.other.Foo;

        @Backing(type="int")
        enum Mode { A = 1, B = 2 }

        /** Docs. */
        @VintfStability
        interface IEverything {
            const int LIMIT = 1 << 4;
            const String NAME = "all; of it";
            const long BIG = 0x7fffffffL;

            @nullable String describe(in @nullable String[] names, out int[] counts, inout List<String> tags);
            oneway void fire(in Foo foo, in Bundle extras);
            Map<String, Foo> index(in ParcelFileDescriptor descriptor, IBinder binder, in int[3] fixed);
            int withId(int a) = 4;
            parcelable Nested { int x; }
        }
    """.trimIndent() + "\n"

    val javaTypes = """
        package p;

        import android.os.Bundle;
        import q.Foo;

        interface IMapped {
            const long LIMIT = 10;
            const String NAME = "n";
            const boolean ENABLED = true;

            String a(in CharSequence b, List<String> c, Map<String, Foo> d);
            Foo[] b(in Bundle e, in int[] f, long g, IBinder h, in ParcelFileDescriptor i);
            List<Foo> c(in FileDescriptor j, in Bar k, in q.Baz l, boolean m, char n);
            byte[] d();
        }
    """.trimIndent() + "\n"
}
