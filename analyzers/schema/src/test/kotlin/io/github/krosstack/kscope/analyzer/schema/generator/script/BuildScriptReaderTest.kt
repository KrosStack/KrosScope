package io.github.krosstack.kscope.analyzer.schema.generator.script

import kotlin.test.Test
import kotlin.test.assertEquals

private fun described(script: String): List<String> = BuildScriptReader(script).inputs().map {
    "${it.kind.name} ${it.path.anchor.name} ${it.path.relative}"
}

private fun settingsOf(script: String): OpenApiSettings? = BuildScriptReader(script).inputs().first().openApi

class BuildScriptReaderTest {
    @Test
    fun `the generators of a Kotlin build script give their inputs by a set call`() {
        val script = """
            plugins { id("org.openapi.generator") }

            openApiGenerate {
                generatorName.set("kotlin")
                inputSpec.set("${'$'}rootDir/../spec/openapi/api.yaml")
                packageName.set("sample.api")
            }

            wire {
                sourcePath {
                    srcDir("${'$'}rootDir/../spec/proto")
                }
                kotlin {}
            }
        """.trimIndent()

        assertEquals(
            listOf("OPENAPI_SPEC BUILD_ROOT ../spec/openapi/api.yaml", "WIRE_SOURCE_PATH BUILD_ROOT ../spec/proto"),
            described(script),
        )
    }

    @Test
    fun `an input sits at its call, and the symbol is named after the name the call is made with`() {
        val script = "openApiGenerate {\n    inputSpec.set(\"a.yaml\")\n}\n"
        val input = BuildScriptReader(script).inputs().single()

        assertEquals("inputSpec.set(\"a.yaml\")", script.substring(input.call.start, input.call.end))
        assertEquals("inputSpec", script.substring(input.name.start, input.name.end))
        assertEquals("a.yaml", input.path.name)
    }

    @Test
    fun `a Groovy build script gives its inputs by assignment, by a command and by a set call`() {
        val script = """
            openApiGenerate {
                generatorName = 'kotlin'
                inputSpec = "${'$'}rootDir/a.yaml"
            }
            openApiGenerate {
                inputSpec '../b.yaml'
            }
            openApiGenerate {
                inputSpec.set('${'$'}projectDir/c.yaml'.replace('x', 'y'))
                inputSpec.set("${'$'}projectDir/d.yaml")
            }
            wire {
                sourcePath { srcDir '${'$'}rootDir' }
                sourcePath 'protos'
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "OPENAPI_SPEC BUILD_ROOT a.yaml",
                "OPENAPI_SPEC PLAIN ../b.yaml",
                "OPENAPI_SPEC MODULE d.yaml",
                "WIRE_SOURCE_PATH PLAIN protos",
            ),
            described(script),
        )
    }

    @Test
    fun `an assignment of a Kotlin build script counts as well`() {
        val script = "openApiGenerate {\n    generatorName = \"kotlin\"\n    inputSpec = \"specs/api.yaml\"\n}\n"

        assertEquals(listOf("OPENAPI_SPEC PLAIN specs/api.yaml"), described(script))
        assertEquals("kotlin", settingsOf(script)?.generatorName)
    }

    @Test
    fun `source paths are given by sourcePath calls and by srcDir calls inside a sourcePath block`() {
        val script = """
            wire {
                sourcePath("one", "two")
                sourcePath("three") { srcDir("four") }
                sourcePath {
                    srcDir("five")
                    srcDir("six")
                }
                srcDir("seven")
            }
        """.trimIndent()

        assertEquals(
            listOf("one", "two", "three", "four", "five", "six").map { "WIRE_SOURCE_PATH PLAIN $it" },
            described(script),
        )
    }

    @Test
    fun `the proto path of Wire is no input, and neither are the filters of a source path`() {
        val script = """
            wire {
                protoPath { srcDir("imports") }
                protoPath("more-imports")
                sourcePath {
                    srcDir("protos")
                    include("a/*.proto")
                    exclude("b/*.proto")
                }
            }
        """.trimIndent()

        assertEquals(listOf("WIRE_SOURCE_PATH PLAIN protos"), described(script))
    }

    @Test
    fun `what a comment or a string says is never taken for code`() {
        val script = """
            // openApiGenerate { inputSpec.set("line.yaml") }
            /* wire { sourcePath("block") } */
            val text = "wire { sourcePath(\"string\") }"
            val groovy = 'openApiGenerate { inputSpec "single.yaml" }'
            val raw = ""${'"'}openApiGenerate { inputSpec.set("raw.yaml") }""${'"'}
            wire {
                // sourcePath("hidden")
                sourcePath("shown") /* sourcePath("also hidden") */
            }
        """.trimIndent()

        assertEquals(listOf("WIRE_SOURCE_PATH PLAIN shown"), described(script))
    }

    @Test
    fun `a path that is not a literal is skipped without a word`() {
        val script = """
            openApiGenerate {
                inputSpec.set(file("api.yaml").path)
                inputSpec.set("${'$'}buildDir/api.yaml")
                inputSpec.set("${'$'}{layout.buildDirectory}/api.yaml")
                inputSpec.set("a" + suffix)
                inputSpec.set(specPath)
                inputSpec = computed()
                inputSpec.set("kept.yaml")
            }
            wire {
                sourcePath { srcDir(dir) }
                sourcePath(project.layout.projectDirectory.dir("p"))
                sourcePath("${'$'}name/protos")
            }
        """.trimIndent()

        assertEquals(listOf("OPENAPI_SPEC PLAIN kept.yaml"), described(script))
    }

    @Test
    fun `a block that is not called openApiGenerate or wire gives nothing`() {
        val script = "tasks.register(\"wire\") { inputSpec.set(\"a\") }\nother { sourcePath(\"b\") }\n"

        assertEquals(emptyList(), described(script))
    }

    @Test
    fun `an unclosed block and a call without an end still read`() {
        assertEquals(listOf("WIRE_SOURCE_PATH PLAIN a"), described("wire { sourcePath(\"a\""))
        assertEquals(listOf("OPENAPI_SPEC PLAIN a"), described("openApiGenerate { inputSpec.set(\"a\")"))
    }

    @Test
    fun `a script that mentions neither generator is not worth reading`() {
        assertEquals(false, BuildScriptReader.mayHaveInputs("plugins { kotlin(\"jvm\") }"))
        assertEquals(true, BuildScriptReader.mayHaveInputs("wire {}"))
        assertEquals(true, BuildScriptReader.mayHaveInputs("openApiGenerate {}"))
    }
}
