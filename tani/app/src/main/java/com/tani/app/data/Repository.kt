package com.tani.app.data
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
class Repository {
 suspend fun login(email:String,password:String){require(email.isNotBlank()){ "البريد الإلكتروني مطلوب" };require(password.length>=6){ "كلمة المرور قصيرة" };val r:AuthResponse=Supabase.authPost("token?grant_type=password",buildJsonObject{put("email",email.trim());put("password",password)}.toString());Supabase.token=r.access_token?:error("تعذر تسجيل الدخول");Supabase.userId=r.user?.id}
 suspend fun signup(email:String,password:String,name:String,phone:String){require(name.isNotBlank()){ "الاسم مطلوب" };require(phone.isNotBlank()){ "رقم الهاتف مطلوب" };require(email.isNotBlank()){ "البريد الإلكتروني مطلوب" };require(password.length>=6){ "كلمة المرور يجب أن تكون 6 أحرف على الأقل" };val body=buildJsonObject{put("email",email.trim());put("password",password);put("data",buildJsonObject{put("name",name.trim());put("phone",phone.trim())})};val r:AuthResponse=Supabase.authPost("signup",body.toString());val uid=r.user?.id?:error("تعذر إنشاء الحساب");if(r.access_token!=null){Supabase.token=r.access_token;Supabase.userId=uid;Supabase.post<JsonElement>("profiles",buildJsonObject{put("id",uid);put("name",name.trim());put("phone",phone.trim());put("role","customer")}.toString())}}
 fun logout()=Supabase.clearSession()
 suspend fun products():List<Product>=Supabase.get("products","select=*&is_active=eq.true&order=created_at.desc")
 suspend fun becomeSeller(storeName:String){val uid=Supabase.userId?:error("تسجيل الدخول مطلوب");require(storeName.isNotBlank()){ "اسم المتجر مطلوب" };if(seller()!=null)error("لديك طلب متجر بالفعل");Supabase.post<JsonElement>("sellers",buildJsonObject{put("user_id",uid);put("store_name",storeName.trim());put("verification_status","pending")}.toString())}
 suspend fun seller():Seller?{val uid=Supabase.userId?:return null;return Supabase.get<List<Seller>>("sellers","select=*&user_id=eq.$uid&limit=1").firstOrNull()}
 suspend fun sellerProducts(id:String):List<Product>=Supabase.get("products","select=*&seller_id=eq.$id&order=created_at.desc")
 suspend fun addProduct(sellerId:String,name:String,description:String,price:Double,stock:Int){require(name.isNotBlank()){ "اسم المنتج مطلوب" };require(price>0){ "السعر يجب أن يكون أكبر من صفر" };require(stock>=0){ "المخزون غير صحيح" };Supabase.post<JsonElement>("products",buildJsonObject{put("seller_id",sellerId);put("name",name.trim());put("description",description.trim());put("price",price);put("stock",stock);put("is_active",true)}.toString())}
 suspend fun orders():List<Order>{val uid=Supabase.userId?:return emptyList();return Supabase.get("orders","select=*&customer_id=eq.$uid&order=created_at.desc")}
 suspend fun sellerOrderItems(sellerId:String):List<OrderItem>=Supabase.get("order_items","select=*&seller_id=eq.$sellerId&order=created_at.desc")
 suspend fun placeOrder(address:String,phone:String,items:List<CartItem>){require(address.isNotBlank()){ "العنوان مطلوب" };require(phone.isNotBlank()){ "رقم الهاتف مطلوب" };require(items.isNotEmpty()){ "السلة فارغة" };require(items.map{it.product.seller_id}.distinct().size==1){ "في نسخة MVP يجب أن يكون الطلب من متجر واحد فقط" };val payload=buildJsonArray{items.forEach{add(buildJsonObject{put("product_id",it.product.id);put("quantity",it.quantity)})}};Supabase.post<JsonElement>("rpc/place_order",buildJsonObject{put("p_address",address.trim());put("p_phone",phone.trim());put("p_items",payload)}.toString())}
}
