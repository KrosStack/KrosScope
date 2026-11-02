package io.github.krosstack.kscope.analyzer.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FileResourcesTest : ProducerTestBase() {

    @Test
    fun `a layout is a resource, declares its ids and refers to resources, includes and ids`() {
        val lines = linesOf(layout)
        assertEquals(
            listOf(
                "symbol android.resource.layout :app@main:android/res/layout/activity_main. 1:1-1:1",
                "symbol android.resource.id :app@main:android/res/id/root. 5:22-5:26",
                "symbol android.resource.id :app@main:android/res/id/title. 13:26-13:31",
                "symbol android.resource.id :app@main:android/res/id/badge. 25:26-25:31",
                "symbol android.resource.id :app@main:android/res/id/custom. 28:26-28:32",
            ),
            lines.filter { it.startsWith("symbol") },
        )
        assertEquals(
            listOf(
                "ref :app@main:android/res/layout/activity_main. -> color/surface 8:25-8:39",
                "ref :app@main:android/res/layout/activity_main. -> dimen/padding 9:22-9:36",
                "ref :app@main:android/res/layout/activity_main. -> string/app_name 16:23-16:39",
                "ref :app@main:android/res/layout/activity_main. -> attr/textAppearanceHeadline6 17:33-17:62",
                "ref :app@main:android/res/layout/activity_main. -> style/Widget.Title 20:16-20:35",
                "ref :app@main:android/res/layout/activity_main. -> string/greeting 21:21-21:37",
                "ref :app@main:android/res/layout/activity_main. include -> layout/view_badge 24:17-24:35",
                "ref :app@main:android/res/layout/activity_main. -> color/brand 31:21-31:33",
                "ref :app@main:android/res/layout/activity_main. -> id/title 32:46-32:56",
                "ref :app@main:android/res/layout/activity_main. -> integer/columns 33:25-33:41",
                "ref :app@main:android/res/layout/activity_main. -> string/missing_label 37:23-37:44",
                "ref :app@main:android/res/layout/activity_main. " +
                    "tools-context -> sample.droid.app.MainActivity 10:20-10:33",
                "ref :app@main:android/res/layout/activity_main. " +
                    "custom-view -> com.example.droid.BadgeView 27:6-27:33",
            ),
            lines.filter { it.startsWith("ref") },
        )
    }

    @Test
    fun `an id declared again in a layout and an id used elsewhere are not new symbols`() {
        val lines = linesOf(layout)
        assertEquals(
            listOf("rel REFERENCES/definition activity_main.xml -> :app@main:android/res/id/title. 36:26-36:31"),
            lines.filter { it.startsWith("rel") },
        )
        assertEquals(1, lines.count { "android/res/id/title." in it && it.startsWith("symbol") })
        assertEquals(
            listOf("diagnostic INFO android.resource.external-references"),
            lines.filter { it.startsWith("diag") },
        )
    }

    @Test
    fun `a layout in a qualified configuration is the same resource with its own definition site`() {
        val lines = linesOf("app/src/main/res/layout-land/activity_main.xml")
        assertEquals(
            "symbol android.resource.layout :app@main:android/res/layout/activity_main. 1:1-1:1",
            lines.first(),
        )
        assertTrue(
            "rel REFERENCES/definition activity_main.xml -> :app@main:android/res/layout/activity_main. 1:1-1:1" in
                lines,
        )
        assertTrue("ref :app@main:android/res/layout/activity_main. -> string/in_two_libs 10:37-10:56" in lines)
    }

    @Test
    fun `navigation destinations are ids that other elements refer to as destinations`() {
        assertEquals(
            listOf(
                "ref :app@main:android/res/navigation/nav_main. navigation-destination -> id/home 5:27-5:35",
                "ref :app@main:android/res/navigation/nav_main. -> string/app_name 10:24-10:40",
                "ref :app@main:android/res/navigation/nav_main. navigation-destination -> id/details 13:30-13:41",
                "ref :app@main:android/res/navigation/nav_main. navigation-destination -> id/home 14:26-14:34",
                "ref :app@main:android/res/navigation/nav_main. -> string/greeting 20:24-20:40",
                "ref :app@main:android/res/navigation/nav_main. " +
                    "destination-class -> com.example.droid.HomeFragment 9:23-9:53",
                "ref :app@main:android/res/navigation/nav_main. " +
                    "destination-class -> com.example.droid.DetailsFragment 19:23-19:56",
            ),
            linesOf("app/src/main/res/navigation/nav_main.xml").filter { it.startsWith("ref") },
        )
        assertEquals(5, linesOf("app/src/main/res/navigation/nav_main.xml").count { it.startsWith("symbol") })
    }

    @Test
    fun `menus and xml drawables and other xml resources refer to resources`() {
        assertEquals(
            listOf(
                "symbol android.resource.menu :app@main:android/res/menu/menu_main. 1:1-1:1",
                "symbol android.resource.id :app@main:android/res/id/action_settings. 4:26-4:41",
                "ref :app@main:android/res/menu/menu_main. -> drawable/ic_shape 5:23-5:41",
                "ref :app@main:android/res/menu/menu_main. -> string/app_name 6:24-6:40",
            ),
            linesOf("app/src/main/res/menu/menu_main.xml"),
        )
        assertEquals(2, linesOf("app/src/main/res/drawable/ic_shape.xml").count { it.startsWith("ref") })
        assertEquals(1, linesOf("app/src/main/res/xml/preferences.xml").count { it.startsWith("ref") })
    }

    @Test
    fun `binary files are resources named after the file without its extensions`() {
        assertEquals(
            listOf("symbol android.resource.drawable :app@main:android/res/drawable/ic_logo. 1:1-1:1"),
            linesOf("app/src/main/res/drawable/ic_logo.png"),
        )
        assertEquals(
            listOf("symbol android.resource.drawable :app@main:android/res/drawable/nine. 1:1-1:1"),
            linesOf("app/src/main/res/drawable/nine.9.png"),
        )
        assertEquals(
            listOf("symbol android.resource.raw :app@main:android/res/raw/license. 1:1-1:1"),
            linesOf("app/src/main/res/raw/license.txt"),
        )
        assertEquals(
            listOf("symbol android.resource.mipmap :app@main:android/res/mipmap/ic_launcher. 1:1-1:1"),
            linesOf("app/src/main/res/mipmap-hdpi/ic_launcher.png").filter { it.startsWith("symbol") },
        )
    }

    @Test
    fun `an id that another attribute declares is a symbol, and that attribute still refers to it`() {
        val xml = """
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@id/header" />
                <TextView android:id="@+id/body" android:layout_below="@+id/header" />
            </RelativeLayout>
        """.trimIndent()
        val lines = Render.lines(factsOf(layout, SampleTree("droid", overrides = mapOf(layout to xml))))
        assertEquals(
            listOf(
                "symbol android.resource.id :app@main:android/res/id/body. 3:32-3:36",
                "symbol android.resource.id :app@main:android/res/id/header. 3:65-3:71",
            ),
            lines.filter { it.startsWith("symbol android.resource.id") },
        )
        assertEquals(
            listOf(
                "ref :app@main:android/res/layout/activity_main. -> id/header 2:27-2:37",
                "ref :app@main:android/res/layout/activity_main. -> id/header 3:60-3:71",
            ),
            lines.filter { it.startsWith("ref") },
        )
    }
}
