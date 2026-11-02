package io.github.krosstack.kscope.gradle.model

import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * The Kotlin and Android Gradle plugins live in a class loader this code cannot see, so their objects are only
 * touched by name. Gradle types (providers, files, source directory sets) are shared and used directly.
 */
internal object Reflect {
    fun get(target: Any?, property: String): Any? {
        if (target == null) return null
        val getter = getterNames(property).firstNotNullOfOrNull { findMethod(target, it, 0) }
        return getter?.let { invoke(it, target, emptyList()) }
    }

    fun has(target: Any?, property: String): Boolean =
        target != null && getterNames(property).any { findMethod(target, it, 0) != null }

    fun call(target: Any?, name: String, vararg args: Any?): Any? {
        if (target == null) return null
        val method = findMethod(target, name, args.size)
            ?: throw NoSuchMethodException("${target.javaClass.name}.$name/${args.size}")
        return invoke(method, target, args.asList())
    }

    fun proxy(type: Class<*>, onInvoke: (Array<out Any?>) -> Any?): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "kscope-proxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> onInvoke(args ?: emptyArray())
            }
        }

    private fun getterNames(property: String): List<String> {
        val capitalized = property.replaceFirstChar { it.uppercase() }
        return listOf("get$capitalized", "is$capitalized", property)
    }

    private fun findMethod(target: Any, name: String, argumentCount: Int): Method? =
        target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterCount == argumentCount && !it.isSynthetic
        }

    private fun invoke(method: Method, target: Any, args: List<Any?>): Any? = try {
        makeAccessible(method)
        when (args.size) {
            0 -> method.invoke(target)
            1 -> method.invoke(target, args[0])
            else -> method.invoke(target, args[0], args[1])
        }
    } catch (failure: InvocationTargetException) {
        throw failure.targetException
    }

    private fun makeAccessible(method: Method) {
        try {
            method.isAccessible = true
        } catch (_: InaccessibleObjectException) {
            return
        }
    }
}
