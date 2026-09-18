package com.tani.app.data.commerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CheckoutQuantityPolicyTest {
    @Test
    fun aggregatesDuplicateProducts() {
        val result = CheckoutQuantityPolicy.aggregate(
            listOf("product-a" to 40, "product-a" to 59, "product-b" to 2)
        )

        assertEquals(99, result["product-a"])
        assertEquals(2, result["product-b"])
    }

    @Test
    fun rejectsAggregateAboveMaximum() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutQuantityPolicy.aggregate(listOf("product-a" to 99, "product-a" to 1))
        }
    }

    @Test
    fun rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutQuantityPolicy.aggregate(listOf("product-a" to 0))
        }
    }
}
