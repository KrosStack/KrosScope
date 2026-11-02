package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

internal object AidlArtifactTypes {
    val source = ArtifactType("aidl.source", ArtifactCategory.SOURCE)
}

/** Claims the `.aidl` files below a directory named `aidl`, which is where Android build tools look for them. */
internal class AidlSourceDetector : ArtifactDetector {
    override val id: String = AidlArtifactTypes.source.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val segments = candidate.path.value.split('/')
        val isAidlFile = segments.last().endsWith(AIDL_EXTENSION, ignoreCase = true)
        val isBelowAidlRoot = segments.dropLast(1).contains(AIDL_DIRECTORY)
        return AidlArtifactTypes.source.takeIf { isAidlFile && isBelowAidlRoot }
    }

    private companion object {
        const val AIDL_EXTENSION = ".aidl"
        const val AIDL_DIRECTORY = "aidl"
    }
}
