package com.tani.app.data.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantDashboardPolicyTest {
    @Test
    fun deliveryPausePreventsReadyState() {
        assertEquals(
            MerchantOperationalState.DELIVERY_PAUSED,
            MerchantDashboardPolicy.operationalState(
                storeExists = true,
                storeActive = true,
                storeOpen = true,
                deliveryConfigured = true,
                deliveryActive = false,
                totalDeliveryZones = 2,
                activeDeliveryZones = 2
            )
        )
    }

    @Test
    fun openStoreWithoutActiveZoneIsNotReady() {
        assertEquals(
            MerchantOperationalState.NO_ACTIVE_DELIVERY_ZONES,
            MerchantDashboardPolicy.operationalState(
                storeExists = true,
                storeActive = true,
                storeOpen = true,
                deliveryConfigured = true,
                deliveryActive = true,
                totalDeliveryZones = 2,
                activeDeliveryZones = 0
            )
        )
    }

    @Test
    fun readyRequiresVisibleOpenStoreAndDelivery() {
        assertEquals(
            MerchantOperationalState.READY,
            MerchantDashboardPolicy.operationalState(
                storeExists = true,
                storeActive = true,
                storeOpen = true,
                deliveryConfigured = true,
                deliveryActive = true,
                totalDeliveryZones = 3,
                activeDeliveryZones = 2
            )
        )
    }

    @Test
    fun readinessCountsOnlyCompletedChecks() {
        val readiness = MerchantDashboardPolicy.readiness(
            storeExists = true,
            storeVisible = true,
            hasLogo = true,
            hasCover = false,
            deliveryConfigured = true,
            hasActiveDeliveryZone = true,
            hasActiveProduct = false
        )

        assertEquals(5, readiness.completed)
        assertEquals(7, readiness.total)
    }

    @Test
    fun activeOrdersExcludeTerminalOrders() {
        assertEquals(4, MerchantDashboardPolicy.activeOrderCount(total = 10, delivered = 4, cancelled = 2))
        assertEquals(0, MerchantDashboardPolicy.activeOrderCount(total = 2, delivered = 2, cancelled = 3))
    }
}
