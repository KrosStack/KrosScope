package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.mangling.JniMangler
import io.github.krosstack.kscope.analyzer.jni.mangling.JvmDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JniManglerTest {
    @Test
    fun `a short name is the class path and the method joined by underscores`() {
        assertEquals(
            "Java_sample_tasktide_nativebridge_Checksum_crc32",
            JniMangler.shortName("sample/tasktide/nativebridge/Checksum", "crc32"),
        )
        assertEquals("Java_Plain_run", JniMangler.shortName("Plain", "run"))
    }

    @Test
    fun `an underscore, a semicolon and a bracket are escaped`() {
        assertEquals("Java_my_1pkg_My_1Class_native_1method", JniMangler.shortName("my_pkg/My_Class", "native_method"))
        assertEquals("_1_2_3", JniMangler.escape("_;["))
    }

    @Test
    fun `a nested class is separated by an escaped dollar sign`() {
        assertEquals("Java_p_Outer_00024Inner_run", JniMangler.shortName("p/Outer\$Inner", "run"))
    }

    @Test
    fun `characters outside ascii letters and digits become four lowercase hex digits`() {
        assertEquals("Java_p_caf_000e9_run", JniMangler.shortName("p/café", "run"))
        assertEquals("_0d83d_0de00", JniMangler.escape("😀"))
        assertEquals("_00020_0002d", JniMangler.escape(" -"))
    }

    @Test
    fun `a long name adds the descriptors of the parameters`() {
        assertEquals("Java_p_C_f__", JniMangler.longName("p/C", "f", emptyList()))
        assertEquals("Java_p_C_f__I", JniMangler.longName("p/C", "f", listOf("I")))
        assertEquals(
            "Java_p_C_f__I_3Ljava_lang_String_2_3B",
            JniMangler.longName("p/C", "f", listOf("I", "[Ljava/lang/String;", "[B")),
        )
    }

    @Test
    fun `descriptors of primitives, arrays and standard classes are written as the jvm writes them`() {
        val descriptors = JvmDescriptors { null }
        val table = mapOf(
            "Int" to "I", "Long" to "J", "Short" to "S", "Byte" to "B", "Char" to "C", "Boolean" to "Z",
            "Float" to "F", "Double" to "D", "int" to "I", "boolean" to "Z",
            "ByteArray" to "[B", "IntArray" to "[I", "byte[]" to "[B", "int[][]" to "[[I",
            "String" to "Ljava/lang/String;", "String?" to "Ljava/lang/String;", "Any" to "Ljava/lang/Object;",
            "Int?" to "Ljava/lang/Integer;", "Array<String>" to "[Ljava/lang/String;",
            "Array<out String>" to "[Ljava/lang/String;", "Array<IntArray>" to "[[I",
            "String..." to "[Ljava/lang/String;",
            "List<String>" to "Ljava/util/List;", "MutableMap<String, Int>" to "Ljava/util/Map;",
            "java.util.Map<String, String>" to "Ljava/util/Map;", "android.os.Bundle" to "Landroid/os/Bundle;",
            "kotlin.String" to "Ljava/lang/String;", "@Nullable String" to "Ljava/lang/String;",
        )
        table.forEach { (type, descriptor) -> assertEquals(descriptor, descriptors.of(type), type) }
    }

    @Test
    fun `a class of the workspace is found by its simple name and an unknown one has no descriptor`() {
        val descriptors = JvmDescriptors { if (it == "Task") "sample/core/Task" else null }
        assertEquals("Lsample/core/Task;", descriptors.of("Task"))
        assertEquals("[Lsample/core/Task;", descriptors.of("Array<Task>"))
        assertEquals("Lsample/core/Task;", descriptors.of("sample.core.Task"))
        assertNull(descriptors.of("Missing"))
        assertNull(descriptors.of("T"))
        assertNull(descriptors.of("Array<Missing>"))
    }

    @Test
    fun `an array of a boxed Kotlin type is an array of its box and not of the primitive`() {
        val descriptors = JvmDescriptors { null }
        val table = mapOf(
            "Array<Int>" to "[Ljava/lang/Integer;",
            "Array<out Long>" to "[Ljava/lang/Long;",
            "Array<Boolean?>" to "[Ljava/lang/Boolean;",
            "Array<Array<Double>>" to "[[Ljava/lang/Double;",
            "Array<IntArray>" to "[[I",
            "IntArray" to "[I",
            "int[]" to "[I",
        )
        table.forEach { (type, descriptor) -> assertEquals(descriptor, descriptors.of(type), type) }
    }
}
