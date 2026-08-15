package com.comichub.source.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockSourceTest {
    private val source = MockSource()

    @Test
    fun `blank search returns catalog`() = runBlocking {
        val results = source.search("")

        assertEquals(3, results.size)
        assertEquals("星海信使", results.first().title)
    }

    @Test
    fun `search filters by title and tag`() = runBlocking {
        val results = source.search("治愈")

        assertEquals(listOf("雨巷茶馆"), results.map { it.title })
    }

    @Test
    fun `detail and pages form a reading flow`() = runBlocking {
        val detail = source.detail("sky-courier")
        val pages = source.pages(detail.chapters.first().id)

        assertTrue(detail.chapters.isNotEmpty())
        assertEquals(6, pages.size)
        assertEquals(1, pages.first().index)
    }
}
