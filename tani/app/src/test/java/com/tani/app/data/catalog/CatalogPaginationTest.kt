package com.tani.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPaginationTest {
    @Test
    fun keepsOneLookAheadRowOutOfVisiblePage() {
        val page = CatalogPagination.page((1..25).toList(), offset = 0, pageSize = 24)

        assertEquals((1..24).toList(), page.items)
        assertEquals(24, page.nextOffset)
        assertTrue(page.hasMore)
    }

    @Test
    fun marksShortLastPageAsComplete() {
        val page = CatalogPagination.page(listOf("a", "b"), offset = 48, pageSize = 24)

        assertEquals(50, page.nextOffset)
        assertFalse(page.hasMore)
    }

    @Test
    fun clampsPageSizeAndRejectsNegativeOffset() {
        assertEquals(2, CatalogPagination.requestLimit(0))
        assertEquals(51, CatalogPagination.requestLimit(500))
        assertThrows(IllegalArgumentException::class.java) {
            CatalogPagination.page(emptyList<Int>(), offset = -1, pageSize = 24)
        }
    }
}
