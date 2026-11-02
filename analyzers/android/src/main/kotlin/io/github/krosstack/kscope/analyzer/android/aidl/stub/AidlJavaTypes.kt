package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlType

private val primitives = setOf("void", "boolean", "byte", "char", "int", "long", "float", "double")

private val builtIns = mapOf(
    "String" to "java.lang.String",
    "CharSequence" to "java.lang.CharSequence",
    "List" to "java.util.List",
    "Map" to "java.util.Map",
    "IBinder" to "android.os.IBinder",
    "FileDescriptor" to "java.io.FileDescriptor",
    "ParcelFileDescriptor" to "android.os.ParcelFileDescriptor",
    "ParcelableHolder" to "android.os.ParcelableHolder",
)

/**
 * The Java names of the types of an `.aidl` file. A built-in type has the name the generator gives it; any other simple
 * name is the one an import says, else a type of the package of the file.
 */
internal class AidlJavaTypes(private val file: AidlFile) {
    private val importedNames: Map<String, String> =
        file.imports.associate { it.name.substringAfterLast('.') to it.name }

    fun javaName(type: AidlType): String {
        val arguments = type.arguments.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", "<", ">", transform = ::javaName).orEmpty()
        return qualify(type.name) + arguments + "[]".repeat(type.arrayDimensions)
    }

    fun qualify(name: String): String = when {
        name in primitives || '.' in name -> name
        name in builtIns -> builtIns.getValue(name)
        name in importedNames -> importedNames.getValue(name)
        else -> listOfNotNull(file.packageName, name).joinToString(".")
    }
}
