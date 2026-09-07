package com.tani.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val user: AuthUser? = null
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("user_metadata") val userMetadata: JsonObject? = null
)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val phone: String,
    val role: String = "customer",
    val avatar_url: String? = null,
    val is_active: Boolean = true
)

data class AccountProfile(
    val profile: UserProfile,
    val email: String
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val image: String? = null,
    val slug: String? = null,
    val sort_order: Int = 0,
    val is_active: Boolean = true
)

@Serializable
data class FeaturedPlacement(
    val id: String,
    val product_id: String? = null,
    val seller_id: String? = null,
    val placement: String,
    val starts_at: String? = null,
    val ends_at: String? = null,
    val is_active: Boolean = true
)

@Serializable
data class Product(
    val id: String,
    val seller_id: String,
    val category_id: String? = null,
    val name: String,
    val description: String = "",
    val price: Double,
    val stock: Int,
    val image: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val store_name: String? = null,
    val delivery_fee: Double = 0.0,
    val delivery_area: String? = null,
    val estimated_minutes: Int? = null
)

@Serializable
data class ProductCard(
    val id: String,
    val seller_id: String,
    val category_id: String? = null,
    val category_name: String? = null,
    val name: String,
    val description: String = "",
    val price: Double,
    val stock: Int,
    val image: String? = null,
    val created_at: String? = null,
    val store_name: String,
    val verification_status: String = "approved",
    val average_rating: Double = 0.0,
    val review_count: Int = 0,
    val delivery_fee: Double = 0.0,
    val delivery_area: String? = null,
    val estimated_minutes: Int? = null
) {
    fun toProduct() = Product(
        id = id,
        seller_id = seller_id,
        category_id = category_id,
        name = name,
        description = description,
        price = price,
        stock = stock,
        image = image,
        is_active = true,
        created_at = created_at,
        store_name = store_name,
        delivery_fee = delivery_fee,
        delivery_area = delivery_area,
        estimated_minutes = estimated_minutes
    )
}

@Serializable
data class ProductImage(
    val id: String,
    val product_id: String,
    val storage_path: String,
    val sort_order: Int = 0,
    val is_primary: Boolean = false
)

@Serializable
data class ProductVariant(
    val id: String,
    val product_id: String,
    val name: String,
    val sku: String? = null,
    val price: Double? = null,
    val stock: Int = 0,
    val is_active: Boolean = true
)

@Serializable
data class Review(
    val id: String,
    val product_id: String,
    val customer_id: String,
    val rating: Int,
    val comment: String = "",
    val created_at: String? = null
)

@Serializable
data class StoreCard(
    val id: String,
    val merchant_id: String,
    val seller_id: String,
    val name: String,
    val description: String = "",
    val logo_url: String? = null,
    val cover_url: String? = null,
    val city: String,
    val area: String? = null,
    val is_open: Boolean = true,
    val created_at: String? = null,
    val verification_status: String = "approved",
    val delivery_fee: Double = 0.0,
    val delivery_area: String? = null,
    val estimated_minutes: Int? = null,
    val product_count: Int = 0,
    val average_rating: Double = 0.0,
    val review_count: Int = 0
)

@Serializable
data class Seller(
    val id: String,
    val user_id: String,
    val store_name: String,
    val verification_status: String,
    val delivery_fee: Double = 0.0
)

@Serializable
data class Address(
    val id: String,
    val user_id: String,
    val label: String = "المنزل",
    val description: String,
    val area: String? = null,
    val landmark: String? = null,
    val phone: String? = null,
    val delivery_notes: String? = null,
    val is_default: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class RemoteCartItem(
    val cart_item_id: String,
    val cart_id: String,
    val product_id: String,
    val variant_id: String? = null,
    val quantity: Int,
    val seller_id: String,
    val category_id: String? = null,
    val name: String,
    val description: String = "",
    val price: Double,
    val stock: Int,
    val image: String? = null,
    val store_name: String,
    val delivery_fee: Double = 0.0,
    val delivery_area: String? = null,
    val estimated_minutes: Int? = null,
    val updated_at: String? = null
) {
    fun toCartItem() = CartItem(
        product = Product(
            id = product_id,
            seller_id = seller_id,
            category_id = category_id,
            name = name,
            description = description,
            price = price,
            stock = stock,
            image = image,
            is_active = true,
            store_name = store_name,
            delivery_fee = delivery_fee,
            delivery_area = delivery_area,
            estimated_minutes = estimated_minutes
        ),
        quantity = quantity
    )
}

@Serializable
data class OrderGroup(
    val id: String,
    val customer_id: String,
    val subtotal: Double = 0.0,
    val delivery_total: Double = 0.0,
    val discount_total: Double = 0.0,
    val grand_total: Double = 0.0,
    val payment_method: String = "cod",
    val payment_status: String = "pending",
    val status: String = "pending",
    val address_id: String? = null,
    val address_snapshot: JsonObject? = null,
    val customer_note: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class Order(
    val id: String,
    val customer_id: String,
    val total: Double,
    val status: String,
    val address: String,
    val phone: String,
    val delivery_fee: Double = 0.0,
    val order_group_id: String? = null,
    val seller_id: String? = null,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val payment_method: String = "cod",
    val payment_status: String = "pending",
    val customer_name_snapshot: String? = null,
    val store_name_snapshot: String? = null,
    val customer_note: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class OrderItem(
    val id: String,
    val order_id: String,
    val product_id: String,
    val seller_id: String,
    val quantity: Int,
    val unit_price: Double,
    val product_name_snapshot: String? = null,
    val discount_snapshot: Double = 0.0,
    val line_total: Double? = null,
    val created_at: String? = null
)

@Serializable
data class OrderStatusHistory(
    val id: String,
    val order_id: String,
    val from_status: String? = null,
    val to_status: String,
    val changed_by: String? = null,
    val note: String? = null,
    val created_at: String? = null
)

@Serializable
data class CartItem(val product: Product, val quantity: Int)

data class OrderGroupDetails(
    val group: OrderGroup,
    val orders: List<Order>,
    val itemsByOrder: Map<String, List<OrderItem>>,
    val historyByOrder: Map<String, List<OrderStatusHistory>>
)

data class HomeFeed(
    val categories: List<Category>,
    val featuredProducts: List<ProductCard>,
    val newestProducts: List<ProductCard>,
    val popularProducts: List<ProductCard>,
    val stores: List<StoreCard>
)

data class ProductDetails(
    val product: ProductCard,
    val images: List<ProductImage>,
    val variants: List<ProductVariant>,
    val reviews: List<Review>,
    val related: List<ProductCard>,
    val store: StoreCard?
)

enum class ProductSort(val query: String) {
    NEWEST("created_at.desc"),
    PRICE_LOW("price.asc"),
    PRICE_HIGH("price.desc"),
    RATING("average_rating.desc,review_count.desc,created_at.desc")
}

@Serializable
data class MerchantProfile(
    val id: String,
    val user_id: String,
    val seller_id: String? = null,
    val business_name: String,
    val description: String = "",
    val phone: String? = null,
    val whatsapp: String? = null,
    val category_id: String? = null,
    val verification_status: String = "pending",
    val trust_badge: Boolean = false,
    val approved_at: String? = null,
    val suspended_at: String? = null,
    val phone_verified_at: String? = null,
    val policies_accepted_at: String? = null,
    val policy_version: String? = null,
    val submitted_at: String? = null,
    val review_note: String? = null,
    val requested_changes_at: String? = null,
    val store_name: String? = null,
    val store_description: String? = null,
    val city: String = "كوستي",
    val area: String? = null,
    val delivery_area: String? = null,
    val delivery_fee: Double = 0.0,
    val estimated_minutes: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class MerchantIdentityDocument(
    val id: String,
    val merchant_id: String,
    val user_id: String,
    val document_type: String,
    val storage_path: String,
    val created_at: String? = null,
    val reviewed_at: String? = null
)

@Serializable
data class MerchantStore(
    val id: String,
    val merchant_id: String,
    val seller_id: String? = null,
    val category_id: String? = null,
    val name: String,
    val description: String = "",
    val logo_url: String? = null,
    val cover_url: String? = null,
    val city: String = "كوستي",
    val area: String? = null,
    val contact_phone: String? = null,
    val whatsapp: String? = null,
    val is_open: Boolean = true,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class DeliverySettings(
    val id: String,
    val seller_id: String,
    val base_fee: Double = 0.0,
    val delivery_area: String? = null,
    val estimated_minutes: Int? = null,
    val notes: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class MerchantBestSeller(
    val id: String,
    val name: String,
    val units: Int = 0,
    val revenue: Double = 0.0,
    val views: Int = 0
)

@Serializable
data class MerchantDashboardSummary(
    val seller_id: String,
    val orders: Int = 0,
    val delivered_orders: Int = 0,
    val cancelled_orders: Int = 0,
    val gmv: Double = 0.0,
    val product_views: Int = 0,
    val conversion_rate: Double = 0.0,
    val customers: Int = 0,
    val repeat_customers: Int = 0,
    val products: Int = 0,
    val active_products: Int = 0,
    val average_rating: Double = 0.0,
    val completion_rate: Double = 0.0,
    val cancellation_rate: Double = 0.0,
    val average_response_seconds: Double? = null,
    val best_sellers: List<MerchantBestSeller> = emptyList()
)
