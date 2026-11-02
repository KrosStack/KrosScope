package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class CopySourcePlacesTest {
    @Test
    fun `a path is resolved in the checkout of whichever repository holds it`() = tasktideWorld(
        server = copying(
            "\$rootDir/../tasktide-web/dist/assets",
            "\$projectDir/static",
            "\$rootDir/../tasktide-web",
            "\$rootDir/../tasktide-web/../tasktide-app/build",
            "\$rootDir/../../elsewhere",
            "\$rootDir/..",
            "web",
            "\$rootDir",
        ),
    ).use { world ->
        assertEquals(
            listOf(
                "tasktide-web" to "dist/assets",
                "tasktide-app" to "server/static",
                "tasktide-web" to "",
                "tasktide-app" to "build",
                null to null,
                null to null,
                "tasktide-app" to "server/web",
                "tasktide-app" to "",
            ),
            world.sources().map { it.place() },
        )
    }

    @Test
    fun `a checkout that lies elsewhere than beside the others is found by its location`() = tasktideWorld(
        server = copying("\$rootDir/../far/away/place/dist"),
        extra = listOf(RepositorySpec("place", mapOf("package.json" to "{}"), location = "far/away/place")),
    ).use { world ->
        assertEquals("place" to "dist", world.sources().single().place())
    }

    @Test
    fun `a symbol without a place has no repository and no path attribute`() =
        tasktideWorld(server = copying("\$rootDir/../../elsewhere")).use { world ->
            assertEquals(mapOf("copy.task" to "processResources"), world.sources().single().attributes)
        }

    @Test
    fun `a path in the repository of the script is resolved from the directory of the module or of the build`() =
        tasktideWorld(server = copying("src/web", "\${projectDir}/dist", "\${rootDir}/server/dist")).use { world ->
            assertEquals(
                listOf(
                    "tasktide-app" to "server/src/web",
                    "tasktide-app" to "server/dist",
                    "tasktide-app" to "server/dist",
                ),
                world.sources().map { it.place() },
            )
        }
}
