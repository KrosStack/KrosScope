package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ValueResourcesTest : ProducerTestBase() {

    @Test
    fun `value resources of every kind become symbols named after the resource and located at its name`() {
        assertEquals(
            listOf(
                "symbol android.resource.string :app@main:android/res/string/app_name. 3:19-3:27",
                "symbol android.resource.string :app@main:android/res/string/greeting. 4:19-4:27",
                "symbol android.resource.string :app@main:android/res/string/alias. 5:19-5:24",
                "symbol android.resource.string :app@main:android/res/string/from_lib. 6:19-6:27",
                "symbol android.resource.string :app@main:android/res/string/shadowed. 7:19-7:27",
                "symbol android.resource.string :app@main:android/res/string/library_style. 8:19-8:32",
                "symbol android.resource.string :app@main:android/res/string/platform. 9:19-9:27",
                "symbol android.resource.plurals :app@main:android/res/plurals/items. 11:20-11:25",
                "symbol android.resource.array :app@main:android/res/array/modes. 15:25-15:30",
                "symbol android.resource.id :app@main:android/res/id/spacer. 19:17-19:23",
                "symbol android.resource.dimen :app@main:android/res/dimen/gap. 20:17-20:20",
            ),
            linesOf(strings).filter { it.startsWith("symbol") },
        )
    }

    @Test
    fun `references inside value resources belong to the resource that contains them`() {
        assertEquals(
            listOf(
                "ref :app@main:android/res/string/alias. -> string/app_name 5:26-5:42",
                "ref :app@main:android/res/string/from_lib. -> string/lib_title 6:29-6:46",
                "ref :app@main:android/res/string/shadowed. -> string/lib_only 7:29-7:45",
                "ref :app@main:android/res/string/library_style. -> string/abc_action_bar_home_description 8:34-8:73",
                "ref :app@main:android/res/plurals/items. -> string/app_name 12:30-12:46",
                "ref :app@main:android/res/array/modes. -> string/greeting 16:15-16:31",
            ),
            linesOf(strings).filter { it.startsWith("ref") },
        )
    }

    @Test
    fun `a second definition in one file is a definition site and framework references are counted once`() {
        val lines = linesOf(strings)
        assertTrue(
            "rel REFERENCES/definition strings.xml -> :app@main:android/res/string/app_name. 10:19-10:27" in lines,
        )
        assertEquals(1, lines.count { it.startsWith("rel") })
        assertEquals(
            listOf("diagnostic INFO android.resource.external-references"),
            lines.filter { it.startsWith("diag") },
        )
    }

    @Test
    fun `a definition in a qualified configuration is a symbol with the same id and a definition site`() {
        val german = factsOf("app/src/main/res/values-de/strings.xml")
        assertEquals(
            listOf(
                "rel REFERENCES/definition strings.xml -> :app@main:android/res/string/app_name. 3:19-3:27",
                "rel REFERENCES/definition strings.xml -> :app@main:android/res/string/only_german. 4:19-4:30",
            ),
            Render.lines(german).filter { it.startsWith("rel") },
        )
        val defaultSymbol = factsOf(strings).filterIsInstance<Fact.OfSymbol>().first().value
        val germanSymbol = german.filterIsInstance<Fact.OfSymbol>().first().value
        assertEquals(defaultSymbol.id, germanSymbol.id)
        assertEquals("string app_name", defaultSymbol.signature)
        assertEquals("string app_name(values-de)", germanSymbol.signature)
        assertTrue(defaultSymbol.signature.orEmpty() < germanSymbol.signature.orEmpty())
        assertEquals(mapOf("android.resource.qualifiers" to "de"), germanSymbol.attributes)
        assertEquals(emptyMap(), defaultSymbol.attributes)
    }

    @Test
    fun `a resource defined in a variant source set has its own symbol in that source set`() {
        val lines = linesOf("app/src/debug/res/values/strings.xml")
        assertEquals(":app@debug:android/res/string/app_name.", Regex("(:app@debug:\\S+)").find(lines.first())?.value)
        assertTrue("ref :app@debug:android/res/string/debug_banner. -> string/app_name 4:33-4:49" in lines)
    }

    @Test
    fun `a symbol is public, named by namespace and generated field name and located by its source`() {
        val symbols = factsOf("app/src/main/res/values/styles.xml").filterIsInstance<Fact.OfSymbol>().map { it.value }
        val theme = symbols.first()
        assertEquals("Theme.Droid", theme.name)
        assertEquals("sample.droid.app.R.style.Theme_Droid", theme.qualifiedName)
        assertEquals(SymbolCategory.RESOURCE, theme.kind.category)
        assertEquals(SymbolOrigin.Source, theme.origin)
        assertEquals(EvidenceClass.STRUCTURAL, theme.evidenceClass)
        assertEquals(Certainty.EXACT, theme.certainty)
        assertEquals(tree.artifact("app/src/main/res/values/styles.xml").sourceSet, theme.scope.sourceSet)
        assertEquals("style Theme.Droid", theme.signature)
    }
}
