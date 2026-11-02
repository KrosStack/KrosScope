package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Attempt
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallResolutionSuccess
import org.jetbrains.kotlin.analysis.api.resolution.KaSingleCallResolutionAttempt
import org.jetbrains.kotlin.analysis.api.resolution.KaSymbolResolutionError
import org.jetbrains.kotlin.analysis.api.resolution.KaSymbolResolutionSuccess
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolution.KtResolvable

/**
 * Asks the compiler what a reference stands for. A failure inside the compiler is not an answer: it is counted and the
 * reference is left out, so one odd construct cannot take the rest of the file with it.
 */
internal class KotlinResolver(private val session: KaSession) {
    var failures = 0
        private set

    fun symbolsOf(element: KtElement): Attempt<KaSymbol> = guarded(Attempt.Unavailable) {
        with(session) {
            when (val attempt = (element as? KtResolvable)?.tryResolveSymbols()) {
                is KaSymbolResolutionSuccess -> Attempt.Resolved(attempt.symbols)
                is KaSymbolResolutionError -> Attempt.Erroneous(attempt.candidateSymbols)
                else -> viaReference(element)
            }
        }
    }

    fun iterationFunctions(loop: KtForExpression): Attempt<KaSymbol> = guarded(Attempt.Unavailable) {
        with(session) {
            val attempt = loop.tryResolveCall()
            val calls = listOfNotNull(
                attempt?.iteratorCallAttempt,
                attempt?.hasNextCallAttempt,
                attempt?.nextCallAttempt,
            )
            resolvedSymbols(calls)
        }
    }

    fun delegateFunctions(delegate: KtPropertyDelegate): Attempt<KaSymbol> = guarded(Attempt.Unavailable) {
        with(session) {
            val attempt = delegate.tryResolveCall()
            val calls = listOfNotNull(
                attempt?.valueGetterCallAttempt,
                attempt?.valueSetterCallAttempt,
                attempt?.provideDelegateCallAttempt,
            )
            resolvedSymbols(calls)
        }
    }

    fun overriddenBy(declaration: KtDeclaration): Attempt<KaSymbol> = guarded(Attempt.Unavailable) {
        with(session) {
            val symbol = declaredSymbolOf(declaration) as? KaCallableSymbol
            Attempt.Resolved(symbol?.directlyOverriddenSymbols?.toList().orEmpty())
        }
    }

    fun expectsOf(declaration: KtDeclaration): Attempt<KaSymbol> = guarded(Attempt.Unavailable) {
        with(session) {
            val expects = declaredSymbolOf(declaration)?.getExpectsForActual().orEmpty()
            if (expects.isEmpty()) Attempt.Erroneous(emptyList()) else Attempt.Resolved(expects)
        }
    }

    /** The class a type name stands for: a type alias is expanded and a constructor is replaced by its class. */
    fun classOf(symbol: KaSymbol): KaSymbol = with(session) {
        when (symbol) {
            is KaTypeAliasSymbol -> symbol.expandedType.expandedSymbol ?: symbol
            is KaConstructorSymbol -> symbol.containingClassId?.let { findClass(it) } ?: symbol
            else -> symbol
        }
    }

    fun isPackage(name: FqName): Boolean = guarded(false) { with(session) { findPackage(name) != null } }

    private fun KaSession.declaredSymbolOf(declaration: KtDeclaration): KaDeclarationSymbol? {
        val symbol = declaration.symbol
        return (symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol
    }

    private fun viaReference(element: KtElement): Attempt<KaSymbol> {
        val reference = (element as? KtReferenceExpression)?.mainReference ?: return Attempt.Unavailable
        val symbols = with(session) { reference.resolveToSymbols() }
        return if (symbols.isEmpty()) Attempt.Erroneous(emptyList()) else Attempt.Resolved(symbols.toList())
    }

    private fun resolvedSymbols(attempts: List<KaSingleCallResolutionAttempt>): Attempt<KaSymbol> {
        val symbols = attempts.filterIsInstance<KaCallResolutionSuccess>().map { it.call.signature.symbol }
        return if (symbols.isEmpty()) Attempt.Unavailable else Attempt.Resolved(symbols)
    }

    private fun <T : Any> guarded(fallback: T, block: () -> T): T = runCatching(block).getOrElse {
        if (it is OutOfMemoryError) throw it
        failures++
        fallback
    }
}
