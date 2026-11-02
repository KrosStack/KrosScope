package io.github.krosstack.kscope.analyzer.android.viewbinding

/** The names the view binding generator derives from layout names and ids, which are written in snake case. */
internal object BindingNames {
    private const val SEPARATOR = '_'

    /** `fragment_profile_edit` becomes `FragmentProfileEditBinding`. */
    fun classNameOf(layout: String): String = words(layout).joinToString("") { it.upperFirst() } + "Binding"

    /** `save_button` becomes `saveButton`; a name that is camel case already stays as it is. */
    fun fieldNameOf(id: String): String {
        val words = words(id)
        return words.firstOrNull().orEmpty().lowerFirst() + words.drop(1).joinToString("") { it.upperFirst() }
    }

    private fun words(name: String): List<String> = name.split(SEPARATOR).filter { it.isNotEmpty() }

    private fun String.upperFirst() = replaceFirstChar(Char::uppercaseChar)

    private fun String.lowerFirst() = replaceFirstChar(Char::lowercaseChar)
}
