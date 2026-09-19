package com.tani.app.data.catalog

data class CatalogPage<T>(
    val items: List<T>,
    val nextOffset: Int,
    val hasMore: Boolean
)

object CatalogPagination {
    const val DEFAULT_PAGE_SIZE = 24
    const val MAX_PAGE_SIZE = 50

    fun normalizedPageSize(value: Int): Int = value.coerceIn(1, MAX_PAGE_SIZE)

    fun requestLimit(pageSize: Int): Int = normalizedPageSize(pageSize) + 1

    fun <T> page(fetched: List<T>, offset: Int, pageSize: Int): CatalogPage<T> {
        require(offset >= 0) { "إزاحة الصفحة غير صحيحة" }
        val size = normalizedPageSize(pageSize)
        val items = fetched.take(size)
        return CatalogPage(
            items = items,
            nextOffset = offset + items.size,
            hasMore = fetched.size > size
        )
    }
}
