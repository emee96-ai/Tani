package com.tani.app.data.merchant

enum class MerchantOperationalState {
    MISSING_STORE,
    HIDDEN,
    CLOSED,
    DELIVERY_UNCONFIGURED,
    DELIVERY_PAUSED,
    NO_ACTIVE_DELIVERY_ZONES,
    READY
}

data class MerchantReadiness(
    val completed: Int,
    val total: Int
)

object MerchantDashboardPolicy {
    fun operationalState(
        storeExists: Boolean,
        storeActive: Boolean,
        storeOpen: Boolean,
        deliveryConfigured: Boolean,
        deliveryActive: Boolean,
        totalDeliveryZones: Int,
        activeDeliveryZones: Int
    ): MerchantOperationalState = when {
        !storeExists -> MerchantOperationalState.MISSING_STORE
        !storeActive -> MerchantOperationalState.HIDDEN
        !storeOpen -> MerchantOperationalState.CLOSED
        !deliveryConfigured -> MerchantOperationalState.DELIVERY_UNCONFIGURED
        !deliveryActive -> MerchantOperationalState.DELIVERY_PAUSED
        totalDeliveryZones > 0 && activeDeliveryZones == 0 -> MerchantOperationalState.NO_ACTIVE_DELIVERY_ZONES
        totalDeliveryZones == 0 -> MerchantOperationalState.DELIVERY_UNCONFIGURED
        else -> MerchantOperationalState.READY
    }

    fun readiness(
        storeExists: Boolean,
        storeVisible: Boolean,
        hasLogo: Boolean,
        hasCover: Boolean,
        deliveryConfigured: Boolean,
        hasActiveDeliveryZone: Boolean,
        hasActiveProduct: Boolean
    ): MerchantReadiness {
        val checks = listOf(
            storeExists,
            storeVisible,
            hasLogo,
            hasCover,
            deliveryConfigured,
            hasActiveDeliveryZone,
            hasActiveProduct
        )
        return MerchantReadiness(checks.count { it }, checks.size)
    }

    fun activeOrderCount(total: Int, delivered: Int, cancelled: Int): Int =
        (total - delivered - cancelled).coerceAtLeast(0)
}
