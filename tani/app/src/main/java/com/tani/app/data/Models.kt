package com.tani.app.data
import kotlinx.serialization.Serializable
@Serializable data class AuthResponse(val access_token:String?=null,val user:AuthUser?=null)
@Serializable data class AuthUser(val id:String)
@Serializable data class Product(val id:String,val seller_id:String,val category_id:String?=null,val name:String,val description:String="",val price:Double,val stock:Int,val image:String?=null,val is_active:Boolean=true)
@Serializable data class Seller(val id:String,val user_id:String,val store_name:String,val verification_status:String,val delivery_fee:Double=0.0)
@Serializable data class Order(val id:String,val customer_id:String,val total:Double,val status:String,val address:String,val phone:String,val delivery_fee:Double=0.0)
@Serializable data class OrderItem(val id:String,val order_id:String,val product_id:String,val seller_id:String,val quantity:Int,val unit_price:Double)
data class CartItem(val product:Product,val quantity:Int)
