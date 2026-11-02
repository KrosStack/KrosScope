package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.read.SymbolFixtures.alpha
import io.github.krosstack.kscope.store.read.SymbolFixtures.ids
import io.github.krosstack.kscope.store.read.SymbolFixtures.withSymbols
import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolFilterTest {
    @Test
    fun `an empty query returns all symbols in canonical order`() = withSymbols { store ->
        assertEquals(listOf("alpha", "beta", "gamma"), store.ids(SymbolQuery()))
    }

    @Test
    fun `symbols are filtered by name and qualified name`() = withSymbols { store ->
        assertEquals(listOf("alpha", "gamma"), store.ids(SymbolQuery(name = "Alpha")))
        assertEquals(listOf("beta"), store.ids(SymbolQuery(qualifiedName = "p.Alpha.Beta")))
    }

    @Test
    fun `symbols are filtered by kind and category`() = withSymbols { store ->
        assertEquals(listOf("beta"), store.ids(SymbolQuery(kindId = "kotlin.function")))
        assertEquals(listOf("gamma"), store.ids(SymbolQuery(category = SymbolCategory.VARIABLE)))
    }

    @Test
    fun `symbols are filtered by the artifact that declares them`() = withSymbols { store ->
        assertEquals(listOf("beta"), store.ids(SymbolQuery(artifact = Builders.artifactRef("src/B.kt"))))
        assertEquals(emptyList(), store.ids(SymbolQuery(artifact = Builders.artifactRef("src/none.kt"))))
    }

    @Test
    fun `symbols are filtered by container`() = withSymbols { store ->
        assertEquals(listOf("beta", "gamma"), store.ids(SymbolQuery(container = alpha.id)))
    }

    @Test
    fun `symbols are filtered by flag`() = withSymbols { store ->
        assertEquals(listOf("alpha", "beta"), store.ids(SymbolQuery(flag = SymbolFlag.OPEN)))
        assertEquals(listOf("beta"), store.ids(SymbolQuery(flag = SymbolFlag.SUSPEND)))
        assertEquals(emptyList(), store.ids(SymbolQuery(flag = SymbolFlag.EXPECT)))
    }

    @Test
    fun `criteria combine as a conjunction`() = withSymbols { store ->
        assertEquals(listOf("gamma"), store.ids(SymbolQuery(name = "Alpha", container = alpha.id)))
        assertEquals(emptyList(), store.ids(SymbolQuery(name = "Beta", flag = SymbolFlag.EXPECT)))
    }

    @Test
    fun `the limit keeps the canonically first symbols`() = withSymbols { store ->
        assertEquals(listOf("alpha", "beta"), store.ids(SymbolQuery(limit = 2)))
        assertEquals(emptyList(), store.ids(SymbolQuery(limit = 0)))
    }
}
