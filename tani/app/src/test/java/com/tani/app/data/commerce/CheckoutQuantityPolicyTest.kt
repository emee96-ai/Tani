package com.tani.app.data.commerce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CheckoutQuantityPolicyTest {
    @Test
    fun aggregatesDuplicateProducts() {
        val result = CheckoutQuantityPolicy.aggregate(
            listOf(
                CheckoutQuantityPolicy.LineKey("product-a") to 40,
                CheckoutQuantityPolicy.LineKey("product-a") to 59,
                CheckoutQuantityPolicy.LineKey("product-b") to 2
            )
        )

        assertEquals(99, result[CheckoutQuantityPolicy.LineKey("product-a")])
        assertEquals(2, result[CheckoutQuantityPolicy.LineKey("product-b")])
    }

    @Test
    fun rejectsAggregateAboveMaximum() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutQuantityPolicy.aggregate(listOf(
                CheckoutQuantityPolicy.LineKey("product-a", "variant-a") to 99,
                CheckoutQuantityPolicy.LineKey("product-a", "variant-a") to 1
            ))
        }
    }

    @Test
    fun rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutQuantityPolicy.aggregate(listOf(CheckoutQuantityPolicy.LineKey("product-a") to 0))
        }
    }

    @Test
    fun keepsVariantsAsSeparateCartLines() {
        val result = CheckoutQuantityPolicy.aggregate(listOf(
            CheckoutQuantityPolicy.LineKey("product-a", "small") to 3,
            CheckoutQuantityPolicy.LineKey("product-a", "large") to 4
        ))

        assertEquals(3, result[CheckoutQuantityPolicy.LineKey("product-a", "small")])
        assertEquals(4, result[CheckoutQuantityPolicy.LineKey("product-a", "large")])
    }
}
