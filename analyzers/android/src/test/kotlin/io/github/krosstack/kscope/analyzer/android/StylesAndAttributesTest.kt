package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Fact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class StylesAndAttributesTest : ProducerTestBase() {
    private val style = ":app@main:android/res/style/"

    @Test
    fun `style parents are references, the explicit ones as written and the implicit one from the dotted name`() {
        assertEquals(
            listOf(
                "ref $style`Theme.Droid`. style-parent -> style/Theme.AppCompat.Light 3:39-3:60",
                "ref $style`Theme.Droid`. -> color/brand 4:35-4:47",
                "ref $style`Theme.Droid`. -> color/surface 5:47-5:61",
                "ref $style`Theme.Droid.Dark`. style-parent.implicit -> style/Theme.Droid 7:18-7:34",
                "ref $style`Widget.Title`. style-parent -> style/Theme.Droid 8:40-8:58",
                "ref $style`Widget.Title`. -> dimen/title_size 9:39-9:56",
                "ref ${style}Themed. style-parent -> attr/actionBarStyle 13:34-13:54",
            ),
            linesOf("app/src/main/res/values/styles.xml").filter { it.startsWith("ref") },
        )
    }

    @Test
    fun `an empty parent, a framework parent and a style without a dot have no parent reference`() {
        val references = factsOf("app/src/main/res/values/styles.xml").filterIsInstance<Fact.OfSymbolicReference>()
        val keys = references.map { it.value }.filter { it.subKind?.startsWith("style-parent") == true }
        assertEquals(4, keys.size)
        assertTrue(keys.none { "Standalone" in it.key || "Platform" in it.key || "Material" in it.key })
    }

    @Test
    fun `attributes are declared at the top level and in styleables where they say what they are`() {
        val lines = linesOf("app/src/main/res/values/attrs.xml")
        assertEquals(
            listOf(
                "symbol android.resource.attr :app@main:android/res/attr/accent. 3:17-3:23",
                "symbol android.resource.styleable :app@main:android/res/styleable/BadgeView. 4:30-4:39",
                "symbol android.resource.attr :app@main:android/res/attr/badgeCount. 6:21-6:31",
                "symbol android.resource.attr :app@main:android/res/attr/shape. 8:21-8:26",
                "ref :app@main:android/res/styleable/BadgeView. -> attr/accent 5:21-5:27",
                "diagnostic INFO android.resource.external-references",
            ),
            lines,
        )
        val styleable = factsOf("app/src/main/res/values/attrs.xml").filterIsInstance<Fact.OfSymbol>()
            .single { it.value.name == "BadgeView" }.value
        assertEquals("sample.droid.app.R.styleable.BadgeView", styleable.qualifiedName)
    }
}
