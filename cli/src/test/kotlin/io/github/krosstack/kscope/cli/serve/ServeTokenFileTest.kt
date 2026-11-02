package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.token.assertReadableByOwnerOnly
import kotlin.test.Test
import kotlin.test.assertEquals

class ServeTokenFileTest {
    private val metadata = ServeMetadata(1, 2, "secret", "tool", 1, "then")

    @Test
    fun `the token file of a running server can be read by its owner only`() = inWorkspace { root ->
        withTestServer(root) { assertReadableByOwnerOnly(ServeMetadata.file(root)) }
    }

    @Test
    fun `a token file that replaces another one can be read by its owner only`() = inWorkspace { root ->
        metadata.write(root)
        metadata.copy(token = "other").write(root)

        assertReadableByOwnerOnly(ServeMetadata.file(root))
        assertEquals("other", ServeMetadata.read(root)?.token)
    }
}
