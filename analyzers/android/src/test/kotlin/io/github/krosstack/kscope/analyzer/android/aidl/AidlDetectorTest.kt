package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AidlDetectorTest {
    private val detector = AidlSourceDetector()

    private fun detect(path: String) = detector.detect(ArtifactCandidate(RepoPath(path), 0) { ByteArray(0) })?.id

    @Test
    fun `an aidl file below an aidl directory is aidl source`() {
        assertEquals("aidl.source", detect("svc/src/main/aidl/sample/IFoo.aidl"))
        assertEquals("aidl.source", detect("aidl/IFoo.aidl"))
        assertEquals("aidl.source", detect("svc/src/debug/aidl/IFoo.AIDL"))
    }

    @Test
    fun `an aidl file elsewhere and other files below an aidl directory are not`() {
        assertNull(detect("docs/IFoo.aidl"))
        assertNull(detect("svc/src/main/java/IFoo.aidl"))
        assertNull(detect("svc/src/main/aidl/README.md"))
        assertNull(detect("svc/src/main/aidl/IFoo.aidl.orig"))
        assertNull(detect("aidl"))
    }
}
