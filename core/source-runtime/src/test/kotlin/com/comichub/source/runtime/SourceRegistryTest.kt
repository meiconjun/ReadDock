package com.comichub.source.runtime

import kotlin.test.Test
import kotlin.test.assertTrue

class SourceRegistryTest {
    @Test
    fun `default registry has no bundled content source`() {
        assertTrue(SourceRegistry.default().sources.isEmpty())
    }
}
