package io.github.krosstack.kscope.analyzer.android.viewbinding

internal const val VIEW_TYPE = "android.view.View"

/** The Java type of the field for a view tag. Tags without a package are looked up in the platform packages. */
internal object ViewTypes {
    private val viewPackage = setOf("View", "ViewGroup", "ViewStub", "SurfaceView", "TextureView")
    private val webkitPackage = setOf("WebView")

    fun of(tag: String): String = when {
        '.' in tag -> tag
        tag == "fragment" -> VIEW_TYPE
        tag in viewPackage -> "android.view.$tag"
        tag in webkitPackage -> "android.webkit.$tag"
        else -> "android.widget.$tag"
    }
}
