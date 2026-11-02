package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project

/**
 * Collects the variants of Android modules. The callback fires when the project is evaluated; the variants are
 * read later, when the model is requested, because their source providers are not readable inside the callback.
 */
internal object AndroidVariantHook {
    val dslPluginIds = listOf(
        "com.android.application",
        "com.android.library",
        "com.android.dynamic-feature",
        "com.android.test",
    )

    private const val ACTION_TYPE = "org.gradle.api.Action"
    private const val FUNCTION_TYPE = "kotlin.jvm.functions.Function1"

    fun install(project: Project, state: KscopeProjectState) {
        dslPluginIds.forEach { id ->
            project.pluginManager.withPlugin(id) {
                state.errors.guard("android", "variant hook") { register(project, state) }
            }
        }
    }

    private fun register(project: Project, state: KscopeProjectState) {
        val components = project.extensions.findByName("androidComponents") ?: return
        val selector = Reflect.call(Reflect.call(components, "selector"), "all")
        val overloads = components.javaClass.methods.filter { it.name == "onVariants" && it.parameterCount == 2 }
        val method = overloads.firstOrNull { it.parameterTypes[1].name == ACTION_TYPE } ?: overloads.first()
        val callbackType = method.parameterTypes[1]
        val callback = Reflect.proxy(callbackType) { args ->
            args.firstOrNull()?.let(state.variants::add)
            unitFor(callbackType)
        }
        method.invoke(components, selector, callback)
    }

    private fun unitFor(callbackType: Class<*>): Any? {
        if (callbackType.name != FUNCTION_TYPE) return null
        return Class.forName("kotlin.Unit", true, callbackType.classLoader).getField("INSTANCE").get(null)
    }
}
