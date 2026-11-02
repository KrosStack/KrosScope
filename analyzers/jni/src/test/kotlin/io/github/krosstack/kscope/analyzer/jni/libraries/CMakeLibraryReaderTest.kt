package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class CMakeLibraryReaderTest {
    private fun read(text: String) = CMakeLibraryReader().read(text)

    private fun names(text: String) = read(text).map { it.name }

    @Test
    fun `a shared library of the reference build is read with the places of its name and its call`() {
        val text = """
            cmake_minimum_required(VERSION 3.22)
            project(tasktide_checksum CXX)

            find_package(JNI REQUIRED)

            add_library(tasktide_checksum SHARED src/checksum.cpp)
            target_include_directories(tasktide_checksum PRIVATE ${'$'}{JNI_INCLUDE_DIRS})
        """.trimIndent()
        val library = read(text).single()
        assertEquals("tasktide_checksum", library.name)
        assertEquals("cmake.library", library.kind)
        assertEquals("shared", library.linkage)
        assertEquals(Certainty.EXACT, library.certainty)
        assertEquals("add_library(tasktide_checksum SHARED)", library.signature)
        assertEquals(TextRange(6, 13, 6, 30), library.nameRange)
        assertEquals(TextRange(6, 1, 6, 55), library.declaration)
    }

    @Test
    fun `a module library is firm and a call without a type is heuristic`() {
        val libraries = read(
            """
            add_library(plugin MODULE a.c)
            add_library(open a.c b.c)
            add_library(excluded EXCLUDE_FROM_ALL)
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Triple("plugin", "module", Certainty.EXACT),
                Triple("open", "unspecified", Certainty.HEURISTIC),
                Triple("excluded", "unspecified", Certainty.HEURISTIC),
            ),
            libraries.map { Triple(it.name, it.linkage, it.certainty) },
        )
    }

    @Test
    fun `the command is found in any case, with space before the parenthesis and over several lines`() {
        val text = "ADD_LIBRARY (first\n    SHARED\n    a.c\n)\n" +
            "Add_Library(second SHARED a.c)\nmy_add_library(third SHARED)"
        assertEquals(listOf("first", "second"), names(text))
        assertEquals(TextRange(1, 1, 4, 2), read(text).first().declaration)
        assertEquals(TextRange(1, 14, 1, 19), read(text).first().nameRange)
    }

    @Test
    fun `static, object, interface, unknown, imported and alias libraries are skipped`() {
        val text = """
            add_library(a STATIC a.c)
            add_library(b OBJECT b.c)
            add_library(c INTERFACE)
            add_library(d UNKNOWN IMPORTED)
            add_library(e SHARED IMPORTED)
            add_library(f ALIAS a)
            add_library(g SHARED g.c)
        """.trimIndent()
        assertEquals(listOf("g"), names(text))
    }

    @Test
    fun `a name made of variables is not a name`() {
        assertEquals(listOf("literal"), names("add_library(${'$'}{NAME} SHARED a.c)\nadd_library(literal SHARED a.c)"))
    }

    @Test
    fun `comments and quoted arguments are taken for what they are`() {
        val text = """
            # add_library(line SHARED a.c)
            #[[ add_library(bracket SHARED a.c) ]]
            #[==[ add_library(levelled SHARED a.c)
            ]==]
            message("# add_library(string SHARED a.c)")
            add_library("quoted name" SHARED a.c) # add_library(trailing SHARED a.c)
            add_library(real SHARED a.c)
        """.trimIndent()
        assertEquals(listOf("quoted name", "real"), names(text))
        assertEquals(TextRange(7, 13, 7, 17), read(text).last().nameRange)
    }

    @Test
    fun `brackets inside a bracket argument do not hide the libraries after it`() {
        val text = """
            message([[ ( ]])
            add_library(first SHARED a.c)
            message([==[ ) ]] add_library(inner SHARED a.c) ]==] "\"" ( ))
            add_library(second SHARED a.c)
            message(STATUS [=[ (( ]=])
            add_library(third SHARED a.c)
        """.trimIndent()
        assertEquals(listOf("first", "second", "third"), names(text))
    }

    @Test
    fun `a file without the command has no library`() {
        assertEquals(emptyList(), read("project(x)\nadd_executable(x main.c)\n"))
    }
}
