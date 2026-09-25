package com.tani.app.data

import com.tani.app.data.catalog.CatalogPage
import com.tani.app.data.catalog.CatalogPagination

suspend fun Repository.storePage(
    search: String? = null,
    pageSize: Int = CatalogPagination.DEFAULT_PAGE_SIZE,
    offset: Int = 0
): CatalogPage<StoreCard> {
    require(offset >= 0) { "إزاحة الصفحة غير صحيحة" }
    val size = CatalogPagination.normalizedPageSize(pageSize)
    val query = mutableListOf(
        "select=*",
        "order=created_at.desc,id.desc",
        "limit=${CatalogPagination.requestLimit(size)}",
        "offset=$offset"
    )

    sanitizeStoreSearch(search)?.let { term ->
        val pattern = java.net.URLEncoder.encode("*$term*", Charsets.UTF_8.name())
        query += "or=(name.ilike.$pattern,description.ilike.$pattern,city.ilike.$pattern,area.ilike.$pattern)"
    }

    val fetched: List<StoreCard> = Supabase.get(
        "marketplace_store_cards",
        query.joinToString("&")
    )
    return CatalogPagination.page(fetched, offset, size)
}

internal fun sanitizeStoreSearch(value: String?): String? = value
    ?.trim()
    ?.replace(Regex("[,()\\*%]"), " ")
    ?.replace(Regex("\\s+"), " ")
    ?.take(80)
    ?.takeIf { it.length >= 2 }
