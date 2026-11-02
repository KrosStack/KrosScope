package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeneratedAddressTest {
    private val owner = "p.databinding.ItemTaskBinding"
    private val manifest = "app/src/main/AndroidManifest.xml"
    private val twoParameters = "(android.view.LayoutInflater,android.view.ViewGroup,boolean)"

    private fun generated(qualifiedName: String, kind: String, descriptor: SymbolDescriptor): Symbol =
        Graphs.symbol("", qualifiedName, kind, 1, descriptor)
            .copy(declaration = null, nameLocation = null, origin = SymbolOrigin.Generated("android.viewbinding", null))

    private fun method(name: String, parameters: String) =
        generated("$owner.$name", "java.method", Graphs.method("ItemTaskBinding", name, parameters))

    private val inflateShort = method("inflate", "LayoutInflater")
    private val inflateLong = method("inflate", "LayoutInflater,ViewGroup,boolean")
    private val getRoot = method("getRoot", "")
    private val title = generated("$owner.title", "java.field", Graphs.type("ItemTaskBinding").term("title"))

    private val resolver = GoldAddressResolver(FakeGraph(symbols = listOf(inflateShort, inflateLong, getRoot, title)))

    private fun address(fqName: String, kind: String, signature: String? = null) =
        GoldAddress(fqName = fqName, kind = kind, generated = true, signature = signature)

    private fun nodeOf(address: GoldAddress) = assertIs<AddressResolution.Found>(resolver.resolve(address)).node

    private fun reasonOf(resolution: AddressResolution) = assertIs<AddressResolution.Unmapped>(resolution).reason

    @Test
    fun `overloads of a generated method are told apart by the signature gold gives`() {
        val long = address("$owner.inflate", "generated.viewbinding-method", twoParameters)
        assertEquals(NodeRef.OfSymbol(inflateLong.id), nodeOf(long))
        val short = address("$owner.inflate", "generated.viewbinding-method", "(android.view.LayoutInflater)")
        assertEquals(NodeRef.OfSymbol(inflateShort.id), nodeOf(short))
    }

    @Test
    fun `overloads without a signature to choose by are not produced as far as gold can tell`() {
        val unmapped = resolver.resolve(address("$owner.inflate", "generated.viewbinding-method"))
        assertEquals(UnmappedReason.GENERATED_SURFACE_NOT_PRODUCED, reasonOf(unmapped))
    }

    @Test
    fun `a property that kotlin reads through a getter is the getter, and a field is the field`() {
        assertEquals(NodeRef.OfSymbol(getRoot.id), nodeOf(address("$owner.root", "generated.viewbinding-field")))
        assertEquals(NodeRef.OfSymbol(title.id), nodeOf(address("$owner.title", "generated.viewbinding-field")))
        val missing = resolver.resolve(address("$owner.gone", "generated.viewbinding-field"))
        assertEquals(UnmappedReason.GENERATED_SURFACE_NOT_PRODUCED, reasonOf(missing))
    }

    @Test
    fun `a manifest component is found by its kind, name as written and the line of the name`() {
        fun component(kind: String, line: Int) =
            Graphs.symbol(manifest, ".Main", "android.manifest.$kind", line, SymbolDescriptor().term(kind))
                .copy(kind = SymbolKind("android.manifest.$kind", SymbolCategory.COMPONENT), name = ".Main")
        val activity = component("activity", 14)
        val resolver = GoldAddressResolver(FakeGraph(symbols = listOf(activity, component("service", 20))))
        val address = GoldAddress(file = manifest, kind = "manifest.activity", name = ".Main", line = 14)
        assertEquals(NodeRef.OfSymbol(activity.id), assertIs<AddressResolution.Found>(resolver.resolve(address)).node)
        assertIs<AddressResolution.Unmapped>(resolver.resolve(address.copy(kind = "manifest.receiver")))
    }

    @Test
    fun `a layout or a navigation graph is found by the kind gold uses for class references`() {
        val layout = Graphs.symbol("res/layout/a.xml", "a", "android.resource.layout", 1, SymbolDescriptor().term("a"))
            .copy(kind = SymbolKind("android.resource.layout", SymbolCategory.RESOURCE))
        val resolver = GoldAddressResolver(FakeGraph(symbols = listOf(layout)))
        val address = GoldAddress(file = "res/layout/a.xml", kind = "android.layout", name = "a", line = 11)
        assertEquals(NodeRef.OfSymbol(layout.id), assertIs<AddressResolution.Found>(resolver.resolve(address)).node)
    }
}
