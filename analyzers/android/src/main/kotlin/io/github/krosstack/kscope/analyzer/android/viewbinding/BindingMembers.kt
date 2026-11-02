package io.github.krosstack.kscope.analyzer.android.viewbinding

private const val INFLATER = "android.view.LayoutInflater"
private const val VIEW_GROUP = "android.view.ViewGroup"

/** A method of a binding class: the signature as the stub declares it, and the parameter types that name it. */
internal class MethodSpec(
    val name: String,
    val returnType: String,
    val parameters: List<Pair<String, String>>,
    val isStatic: Boolean,
) {
    val signature: String
        get() {
            val modifiers = if (isStatic) "public static" else "public"
            return "$modifiers $returnType $name(${parameters.joinToString(", ") { "${it.first} ${it.second}" }})"
        }

    /** The simple names of the parameter types, which tell overloads apart in a symbol id. */
    val disambiguator: String get() = parameters.joinToString(",") { it.first.substringAfterLast('.') }
}

internal fun BindingClass.fieldSignature(field: BindingField) = "public final ${field.type} ${field.name}"

internal val BindingClass.classSignature: String get() = "public final class $name"

/** The methods the generator declares: the root, the `inflate`s and `bind`; a `merge` layout inflates into a parent. */
internal fun BindingClass.methods(): List<MethodSpec> {
    val inflate = if (isMerge) {
        listOf(MethodSpec("inflate", name, listOf(INFLATER to "inflater", VIEW_GROUP to "parent"), true))
    } else {
        listOf(
            MethodSpec("inflate", name, listOf(INFLATER to "inflater"), true),
            MethodSpec(
                "inflate",
                name,
                listOf(INFLATER to "inflater", VIEW_GROUP to "parent", "boolean" to "attachToParent"),
                true,
            ),
        )
    }
    return listOf(MethodSpec("getRoot", rootType, emptyList(), false)) + inflate +
        MethodSpec("bind", name, listOf(VIEW_TYPE to "rootView"), true)
}
