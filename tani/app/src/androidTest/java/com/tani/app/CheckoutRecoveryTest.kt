package com.tani.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tani.app.data.*
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutRecoveryTest {
    private fun id() = UUID.randomUUID().toString()

    @Test fun encryptedAttemptSurvivesReinitializationAndSessionClear() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = id(); val other = id(); val group = id()
        Supabase.init(context)
        try {
            val attempt = PendingCheckoutStore.begin(owner)
            Supabase.clearSession()
            PendingCheckoutStore.init(context)
            assertEquals(attempt.key, PendingCheckoutStore.begin(owner).key)
            assertNull(PendingCheckoutStore.pending(other))
            PendingCheckoutStore.resolve(owner, attempt.key, group)
            PendingCheckoutStore.init(context)
            assertEquals(group, PendingCheckoutStore.pending(owner)?.groupId)
        } finally { PendingCheckoutStore.finish(owner, group) }
    }

    @Test fun confirmationRetryPreservesExtraItemsAndVariantQuantities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner=id(); val group=id(); val order=id(); val seller=id(); val variantId=id()
        val base=Product(id=id(),seller_id=seller,name="Base",price=100.0,stock=99)
        val variantProduct=Product(id=id(),seller_id=seller,name="Variant",price=100.0,stock=99,has_variants=true)
        val extra=Product(id=id(),seller_id=seller,name="Extra",price=100.0,stock=99)
        val variant=ProductVariant(id=variantId,product_id=variantProduct.id,name="Blue",stock=99)
        val details=OrderGroupDetails(OrderGroup(id=group,customer_id=owner),
            listOf(Order(id=order,customer_id=owner,total=300.0,status="pending",address="QA",phone="QA",seller_id=seller)),
            mapOf(order to listOf(
                OrderItem(id=id(),order_id=order,product_id=base.id,seller_id=seller,quantity=2,unit_price=100.0),
                OrderItem(id=id(),order_id=order,product_id=variantProduct.id,seller_id=seller,quantity=1,unit_price=100.0,
                    variant_snapshot=buildJsonObject { put("id",variantId) }))),emptyMap())
        Supabase.init(context);Cart.init(context)
        try {
            Cart.replace(listOf(CartItem(base,5),CartItem(variantProduct,3,variant),CartItem(extra,2)))
            val attempt=PendingCheckoutStore.begin(owner)
            PendingCheckoutStore.resolve(owner,attempt.key,group)
            Cart.completeCheckout(owner,details)
            assertEquals(3,Cart.quantityFor(base.id,null))
            assertEquals(2,Cart.quantityFor(variantProduct.id,variantId))
            assertEquals(2,Cart.quantityFor(extra.id,null))
            assertTrue(Cart.add(base))
            val retried=PendingCheckoutStore.begin(owner)
            PendingCheckoutStore.resolve(owner,retried.key,group)
            Cart.init(context)
            Cart.completeCheckout(owner,details)
            assertEquals(4,Cart.quantityFor(base.id,null))
            assertEquals(2,Cart.quantityFor(variantProduct.id,variantId))
            assertEquals(2,Cart.quantityFor(extra.id,null))
            assertNull(PendingCheckoutStore.pending(owner))
        } finally { Cart.clear();PendingCheckoutStore.finish(owner,group) }
    }
}
