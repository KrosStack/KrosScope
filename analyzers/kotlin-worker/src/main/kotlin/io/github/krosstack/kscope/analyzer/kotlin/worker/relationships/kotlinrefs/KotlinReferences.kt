package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.offsets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Attempt
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceReporter
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceSite
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Shape
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType

private val annotationShape = Shape.of(RelationKind.REFERENCES, "annotation")
private val callableReferenceShape = Shape.of(RelationKind.REFERENCES, "callable-reference")
private val namedArgumentShape = Shape.of(RelationKind.REFERENCES, "named-argument")

private const val INVOKE = "invoke"

/** Reports the references that Kotlin names make: calls, types, supertypes, values, annotations and imports. */
internal class KotlinReferences(
    private val resolver: KotlinResolver,
    private val targets: KotlinTargets,
    private val reporter: ReferenceReporter,
    private val aliases: KotlinAliases,
    private val syntheticMembers: KotlinSyntheticMembers,
) {
    fun name(name: KtNameReferenceExpression, from: NodeRef) {
        val role = KotlinRoles.of(name)
        val shape = shapeOf(role) ?: return
        val access = if (role == Role.Value) KotlinRoles.accessOf(name) else Access.NONE
        val hint = (name.parent as? KtUserType)?.let(KotlinRoles::typeNameOf) ?: name.getReferencedName()
        val site = ReferenceSite(from, name.offsets(), hint)
        val attempt = attemptOf(name, role)
        val pluginOwner = if (attempt is Attempt.Erroneous) syntheticMembers.ownerOf(name) else null
        if (pluginOwner != null) {
            reporter.reportSyntheticMember(site, shape, pluginOwner)
        } else {
            reporter.report(site, shape, attempt) { targets.map(it, access, shape.allowsLibrary) }
        }
        if (role == Role.Call && isImplicitInvoke(name, attempt)) reportInvokedVariable(name, site)
    }

    fun import(directive: KtImportDirective, fileRef: NodeRef) {
        val reference = directive.importedReference ?: return
        val leaf = (reference as? KtDotQualifiedExpression)?.selectorExpression ?: reference
        val hint = directive.importedFqName?.asString() ?: reference.text
        val site = ReferenceSite(fileRef, reference.offsets(), hint)
        reporter.report(site, Shape.imports, resolver.symbolsOf(leaf)) { targets.map(it, Access.NONE, false) }
    }

    // Calling a property or a variable that has an `invoke` operator is a call of `invoke` and a read of the variable.
    private fun isImplicitInvoke(name: KtNameReferenceExpression, attempt: Attempt<KaSymbol>): Boolean {
        val callees = (attempt as? Attempt.Resolved)?.symbols.orEmpty()
        val isInvoke = callees.any { it is KaNamedFunctionSymbol && it.name.asString() == INVOKE }
        return isInvoke && name.getReferencedName() != INVOKE
    }

    private fun reportInvokedVariable(name: KtNameReferenceExpression, site: ReferenceSite) {
        val variables = (resolver.symbolsOf(name) as? Attempt.Resolved)?.symbols.orEmpty()
            .filterIsInstance<KaVariableSymbol>()
        if (variables.isNotEmpty()) {
            reporter.report(site, Shape.reference, Attempt.Resolved(variables)) { targets.map(it, Access.READ, false) }
        }
    }

    private fun shapeOf(role: Role): Shape? = when (role) {
        Role.Skip -> null
        Role.Call -> Shape.call
        Role.TypeUse -> Shape.typeUse
        is Role.Supertype -> Shape.supertype(role.declaringIsInterface, role.hasConstructorCall, role.anonymous)
        Role.Annotation -> annotationShape
        Role.Value -> Shape.reference
        Role.CallableReference -> callableReferenceShape
        Role.NamedArgument -> namedArgumentShape
    }

    private fun attemptOf(name: KtNameReferenceExpression, role: Role): Attempt<KaSymbol> = when (role) {
        Role.Call -> resolver.symbolsOf(name.parent as KtCallExpression)
        is Role.Supertype, Role.Annotation -> classesOf(resolver.symbolsOf(name))
        Role.NamedArgument -> constructorProperties(resolver.symbolsOf(name))
        else -> classOfCompanionQualifier(name, withoutPackagePrefix(name, resolver.symbolsOf(name)))
    }

    private fun classOfCompanionQualifier(name: KtNameReferenceExpression, attempt: Attempt<KaSymbol>) =
        if (attempt is Attempt.Resolved) {
            Attempt.Resolved(attempt.symbols.map { aliases.classOfImplicitCompanion(name.getReferencedName(), it) })
        } else {
            attempt
        }

    private fun classesOf(attempt: Attempt<KaSymbol>): Attempt<KaSymbol> = when (attempt) {
        is Attempt.Resolved -> Attempt.Resolved(attempt.symbols.map(resolver::classOf))
        else -> attempt
    }

    private fun constructorProperties(attempt: Attempt<KaSymbol>): Attempt<KaSymbol> {
        val properties = (attempt as? Attempt.Resolved)?.symbols.orEmpty()
            .mapNotNull { (it as? KaValueParameterSymbol)?.let(aliases::propertyOf) }
        return if (properties.isEmpty()) Attempt.Unavailable else Attempt.Resolved(properties)
    }

    // A name that fails to resolve may be the first part of a qualified name, such as a package, or the implicit
    // parameter of a lambda whose own call failed to resolve; neither is a reference to anything that is missing.
    private fun withoutPackagePrefix(name: KtNameReferenceExpression, attempt: Attempt<KaSymbol>): Attempt<KaSymbol> {
        val isUnresolved = attempt is Attempt.Erroneous && attempt.candidates.isEmpty()
        val path = if (isUnresolved) KotlinRoles.qualifierPathOf(name) else null
        val isPackage = path != null && resolver.isPackage(FqName(path))
        val isLambdaParameter = isUnresolved && KotlinRoles.isImplicitLambdaParameter(name)
        return if (isPackage || isLambdaParameter) Attempt.Unavailable else attempt
    }
}
