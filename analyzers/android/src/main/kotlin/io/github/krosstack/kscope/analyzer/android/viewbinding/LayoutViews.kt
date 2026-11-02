package io.github.krosstack.kscope.analyzer.android.viewbinding

/** What the field of a view holds: a view class, or the binding of the layout that an `<include>` brings in. */
internal sealed interface ViewType {
    data class Class(val name: String) : ViewType

    data class Included(val layout: String) : ViewType
}

/** A view with an id; [declares] is true when it writes `@+id`, which defines the id resource. */
internal class BoundView(val id: String, val declares: Boolean, val type: ViewType)

/** The views of one layout file with an id, and the view that its root element stands for. */
internal class LayoutViews(val rootType: ViewType.Class, val isMerge: Boolean, val views: List<BoundView>)

/** Whether a layout file has a binding, and the views of it if it does. */
internal sealed interface ReadLayout {
    data class Bindable(val views: LayoutViews) : ReadLayout

    /** The root says `tools:viewBindingIgnore="true"`. */
    data object Ignored : ReadLayout

    /** The root is `<layout>`, which data binding handles. */
    data object DataBinding : ReadLayout

    /** The file has no root element. */
    data object Empty : ReadLayout
}
