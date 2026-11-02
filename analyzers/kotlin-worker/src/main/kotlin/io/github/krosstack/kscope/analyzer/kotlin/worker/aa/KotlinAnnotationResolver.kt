package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/** Qualifies annotation names with the compiler's help; a name it cannot resolve stays as written. */
internal class KotlinAnnotationResolver {
    fun qualifiedName(entry: KtAnnotationEntry): String? = runCatching {
        analyze(entry) {
            val type = entry.typeReference?.type
            (type as? KaClassType)?.classId?.asSingleFqName()?.asString()
        }
    }.getOrNull()
}
