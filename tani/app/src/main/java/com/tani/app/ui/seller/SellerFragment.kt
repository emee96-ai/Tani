package com.tani.app.ui.seller

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.MainActivity
import com.tani.app.ui.growth.MerchantInsightsFragment
import com.tani.app.ui.monetization.MonetizationFragment
import com.tani.app.ui.legal.PoliciesFragment
import com.tani.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class SellerFragment : Fragment(R.layout.fragment_seller) {

    private val repository = Repository()
    private lateinit var box: LinearLayout
    private var merchant: MerchantProfile? = null
    private var seller: Seller? = null
    private var identityPath: String? = null
    private var pendingStoreAsset: String? = null
    private var selectedStoreLogo: String? = null
    private var selectedStoreCover: String? = null
    private var storeLogoStatus: TextView? = null
    private var storeCoverStatus: TextView? = null
    private var pendingProductImageId: String? = null

    private val pickIdentity = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadIdentity(uri)
    }
    private val pickStoreImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadStoreImage(uri)
    }
    private val pickProductImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val productId = pendingProductImageId
        if (uri != null && productId != null) uploadProductImage(uri, productId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        box = view.findViewById(R.id.seller_box)
        loadEntry()
    }

    private fun loadEntry() {
        loading("جاري تحميل حساب التاجر…")
        lifecycleScope.launch {
            runCatching {
                merchant = repository.merchantProfile()
                seller = repository.seller()
                merchant
            }.onSuccess { profile ->
                when (profile?.verification_status) {
                    "approved" -> showHub(profile)
                    "pending" -> showApplicationStatus(profile, "طلبك قيد المراجعة")
                    "changes_requested" -> showApplicationStatus(profile, "تحتاج الإدارة تعديلات على طلبك", canEdit = true)
                    "rejected" -> showApplicationStatus(profile, "تم رفض الطلب ويمكنك تعديل البيانات وإعادة الإرسال", canEdit = true)
                    "suspended" -> showApplicationStatus(profile, "حساب التاجر موقوف مؤقتاً")
                    else -> showOnboarding(profile)
                }
            }.onFailure { showError(it.message ?: "تعذر تحميل بيانات التاجر") }
        }
    }

    private fun showApplicationStatus(profile: MerchantProfile, headline: String, canEdit: Boolean = false) {
        clear()
        heading("حالة طلب التاجر")
        text(headline, 18f, true)
        text("النشاط: ${profile.business_name}")
        text("المتجر: ${profile.store_name ?: "—"}")
        text("الهاتف: ${profile.phone ?: "—"}")
        text("الحالة: ${statusText(profile.verification_status)}")
        profile.review_note?.takeIf { it.isNotBlank() }?.let { note ->
            text("ملاحظة المراجعة: $note", 16f, true)
        }
        if (canEdit) button("تعديل وإعادة الإرسال") { showOnboarding(profile) }
        button("تحديث الحالة") { loadEntry() }
    }

    private fun showHub(profile: MerchantProfile) {
        clear()
        heading("إدارة المتجر")
        text(profile.store_name ?: profile.business_name, 22f, true)
        text("الحالة: معتمد ✓")
        text(if (profile.trust_badge) "شارة الثقة: مفعلة" else "شارة الثقة: قيد البناء حسب الأداء")
        button("لوحة المؤشرات") { showDashboard() }
        button("صحة المخزون ومؤشرات النمو") { seller?.id?.let { (activity as? MainActivity)?.show(MerchantInsightsFragment.newInstance(it)) } }
        button("إدارة المتجر") { showStore() }
        button("المنتجات والمخزون") { showProducts() }
        button("طلبات التاجر") { showOrders() }
        button("التوصيل") { showDelivery() }
        button("الاشتراكات والظهور الممول") { seller?.id?.let { (activity as? MainActivity)?.show(MonetizationFragment.newInstance(it)) } }
        text("أي ظهور مدفوع للعملاء يظهر بوسم «ممول» ولا يختلط بالمحتوى العضوي.", 13f)
    }

    private fun showOnboarding(existing: MerchantProfile?) {
        loading("جاري تجهيز نموذج التسجيل…")
        lifecycleScope.launch {
            runCatching {
                val cats = repository.categories()
                val docs = repository.merchantIdentityDocuments()
                identityPath = docs.firstOrNull()?.storage_path
                cats
            }.onSuccess { categories -> renderOnboarding(existing, categories) }
                .onFailure { showError(it.message ?: "تعذر تجهيز التسجيل") }
        }
    }

    private fun renderOnboarding(existing: MerchantProfile?, categories: List<Category>) {
        clear()
        heading("التسجيل كتاجر")
        text("التسجيل لا يعني الاعتماد. يجب التحقق من الهاتف والهوية ثم مراجعة تاني.")

        val businessName = input("اسم النشاط", existing?.business_name)
        val businessDescription = input("وصف النشاط", existing?.description, multiline = true)
        val phone = input("رقم الهاتف بصيغة دولية مثل +249…", existing?.phone)
        val whatsapp = input("WhatsApp (اختياري)", existing?.whatsapp)

        val otp = input("رمز التحقق المكوّن من 6 أرقام", null, number = true)
        row().apply {
            addView(actionButton("إرسال رمز التحقق") {
                lifecycleScope.launch {
                    runCatching { repository.requestMerchantPhoneVerification(phone.text.toString()) }
                        .onSuccess { toast("تم طلب رمز التحقق. أدخلي الرمز الذي وصلك.") }
                        .onFailure { toast(it.message ?: "تعذر إرسال الرمز") }
                }
            })
            addView(actionButton("تحقق") {
                lifecycleScope.launch {
                    runCatching { repository.verifyMerchantPhone(phone.text.toString(), otp.text.toString()) }
                        .onSuccess { toast("تم التحقق من الهاتف ✓") }
                        .onFailure { toast(it.message ?: "تعذر التحقق من الهاتف") }
                }
            })
        }.also(box::addView)

        val categorySpinner = Spinner(requireContext())
        val categoryNames = categories.map { it.name }
        categorySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames)
        val existingCategory = categories.indexOfFirst { it.id == existing?.category_id }
        if (existingCategory >= 0) categorySpinner.setSelection(existingCategory)
        label("فئة المتجر")
        box.addView(categorySpinner, matchWrap())

        val storeName = input("اسم المتجر", existing?.store_name ?: existing?.business_name)
        val storeDescription = input("وصف المتجر", existing?.store_description ?: existing?.description, multiline = true)
        val city = input("المدينة", existing?.city ?: "كوستي")
        val area = input("المنطقة / الحي", existing?.area)
        val deliveryArea = input("مناطق التوصيل", existing?.delivery_area, multiline = true)
        val deliveryFee = input("رسوم التوصيل", existing?.delivery_fee?.toString(), numberDecimal = true)
        val estimated = input("زمن التوصيل التقريبي بالدقائق", existing?.estimated_minutes?.toString(), number = true)

        label("مستند الهوية")
        val identityStatus = textView(if (identityPath.isNullOrBlank()) "لم يتم رفع مستند" else "تم رفع مستند الهوية ✓")
        box.addView(identityStatus)
        button("رفع الهوية / جواز السفر") { pickIdentity.launch("*/*") }

        val documentSpinner = Spinner(requireContext())
        val docLabels = listOf("بطاقة قومية", "جواز سفر", "مستند آخر")
        documentSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, docLabels)
        box.addView(documentSpinner, matchWrap())

        button("عرض اتفاقية وسياسات التاجر") { (activity as? MainActivity)?.show(PoliciesFragment()) }

        val policies = CheckBox(requireContext()).apply {
            text = "أوافق على سياسات التاجر: صحة البيانات والمنتجات والأسعار والتوفر وتنفيذ الطلبات والتوصيل وعدم التلاعب بالتقييمات أو استخدام المنصة في أنشطة محظورة."
            isChecked = existing?.policies_accepted_at != null
            setPadding(0, 14, 0, 14)
        }
        box.addView(policies)

        button("إرسال الطلب للمراجعة") {
            val category = categories.getOrNull(categorySpinner.selectedItemPosition)
            if (category == null) {
                toast("اختاري فئة المتجر")
                return@button
            }
            val fee = deliveryFee.text.toString().toDoubleOrNull()
            if (fee == null) {
                toast("أدخلي رسوم التوصيل")
                return@button
            }
            val identity = identityPath
            if (identity.isNullOrBlank()) {
                toast("ارفعي مستند الهوية أولاً")
                return@button
            }
            val docType = listOf("national_id", "passport", "other")[documentSpinner.selectedItemPosition.coerceIn(0, 2)]
            lifecycleScope.launch {
                runCatching {
                    repository.submitMerchantApplication(
                        businessName.text.toString(), businessDescription.text.toString(),
                        phone.text.toString(), whatsapp.text.toString(), category.id,
                        storeName.text.toString(), storeDescription.text.toString(), city.text.toString(),
                        area.text.toString(), deliveryArea.text.toString(), fee,
                        estimated.text.toString().toIntOrNull(), identity, docType, policies.isChecked
                    )
                }.onSuccess {
                    toast("تم إرسال طلب التاجر للمراجعة")
                    loadEntry()
                }.onFailure { toast(it.message ?: "تعذر إرسال الطلب") }
            }
        }
    }

    private fun uploadIdentity(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val pair = withContext(Dispatchers.IO) { identityBytes(uri) }
                repository.uploadMerchantIdentity(pair.first, pair.second)
            }.onSuccess { path ->
                identityPath = path
                toast("تم رفع مستند الهوية ✓")
            }.onFailure { toast(it.message ?: "تعذر رفع مستند الهوية") }
        }
    }

    private fun identityBytes(uri: Uri): Pair<ByteArray, String> {
        val mime = requireContext().contentResolver.getType(uri).orEmpty()
        if (mime == "application/pdf") {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة الملف")
            require(bytes.size <= 8 * 1024 * 1024) { "الملف أكبر من 8 ميجابايت" }
            return bytes to "application/pdf"
        }
        return imageBytes(uri, 1600, 85) to "image/jpeg"
    }

    private fun showStore() {
        loading("جاري تحميل المتجر…")
        lifecycleScope.launch {
            runCatching { repository.merchantStore() ?: error("لم يتم إنشاء المتجر بعد") }
                .onSuccess(::renderStore)
                .onFailure { showError(it.message ?: "تعذر تحميل المتجر") }
        }
    }

    private fun renderStore(store: MerchantStore) {
        clear()
        backHeader("إدارة المتجر") { loadEntry() }
        selectedStoreLogo = store.logo_url
        selectedStoreCover = store.cover_url
        val name = input("اسم المتجر", store.name)
        val description = input("الوصف", store.description, multiline = true)
        val city = input("المدينة", store.city)
        val area = input("المنطقة", store.area)
        val contact = input("هاتف التواصل", store.contact_phone)
        val whatsapp = input("WhatsApp", store.whatsapp)
        val open = CheckBox(requireContext()).apply { text = "المتجر مفتوح الآن"; isChecked = store.is_open }
        val visible = CheckBox(requireContext()).apply { text = "المتجر ظاهر للعملاء"; isChecked = store.is_active }
        box.addView(open); box.addView(visible)
        storeLogoStatus = textView(if (store.logo_url.isNullOrBlank()) "لا يوجد شعار" else "الشعار مرفوع ✓")
        storeCoverStatus = textView(if (store.cover_url.isNullOrBlank()) "لا توجد صورة غلاف" else "الغلاف مرفوع ✓")
        box.addView(storeLogoStatus); button("تغيير الشعار") { pendingStoreAsset = "logo"; pickStoreImage.launch("image/*") }
        box.addView(storeCoverStatus); button("تغيير الغلاف") { pendingStoreAsset = "cover"; pickStoreImage.launch("image/*") }
        button("حفظ المتجر") {
            lifecycleScope.launch {
                runCatching {
                    repository.updateMerchantStore(
                        store.id, name.text.toString(), description.text.toString(), city.text.toString(),
                        area.text.toString(), contact.text.toString(), whatsapp.text.toString(),
                        open.isChecked, visible.isChecked, selectedStoreLogo, selectedStoreCover
                    )
                }.onSuccess { toast("تم حفظ المتجر"); showStore() }
                    .onFailure { toast(it.message ?: "تعذر حفظ المتجر") }
            }
        }
    }

    private fun uploadStoreImage(uri: Uri) {
        val kind = pendingStoreAsset ?: return
        lifecycleScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 85) }
                repository.uploadStoreAsset(bytes, kind)
            }.onSuccess { path ->
                if (kind == "logo") {
                    selectedStoreLogo = path
                    storeLogoStatus?.text = "تم اختيار شعار جديد ✓ — اضغطي حفظ المتجر"
                } else {
                    selectedStoreCover = path
                    storeCoverStatus?.text = "تم اختيار غلاف جديد ✓ — اضغطي حفظ المتجر"
                }
                toast("تم رفع الصورة")
            }.onFailure { toast(it.message ?: "تعذر رفع الصورة") }
        }
    }

    private fun showDelivery() {
        val sellerId = seller?.id ?: return showError("حساب التاجر غير مكتمل")
        loading("جاري تحميل إعدادات التوصيل…")
        lifecycleScope.launch {
            runCatching { repository.merchantDeliverySettings(sellerId) }
                .onSuccess { renderDelivery(sellerId, it) }
                .onFailure { showError(it.message ?: "تعذر تحميل التوصيل") }
        }
    }

    private fun renderDelivery(sellerId: String, settings: DeliverySettings?) {
        clear(); backHeader("إعدادات التوصيل") { loadEntry() }
        text("في المرحلة الحالية التاجر مسؤول عن التوصيل ورسومه ومناطقه.")
        val fee = input("رسوم التوصيل", settings?.base_fee?.toString() ?: "0", numberDecimal = true)
        val area = input("مناطق التوصيل", settings?.delivery_area, multiline = true)
        val minutes = input("زمن التوصيل التقريبي بالدقائق", settings?.estimated_minutes?.toString(), number = true)
        val notes = input("ملاحظات التوصيل", settings?.notes, multiline = true)
        val active = CheckBox(requireContext()).apply { text = "التوصيل متاح"; isChecked = settings?.is_active ?: true }
        box.addView(active)
        button("حفظ التوصيل") {
            val f = fee.text.toString().toDoubleOrNull()
            if (f == null) return@button toast("أدخلي رسوم التوصيل")
            lifecycleScope.launch {
                runCatching { repository.saveMerchantDeliverySettings(sellerId, f, area.text.toString(), minutes.text.toString().toIntOrNull(), notes.text.toString(), active.isChecked) }
                    .onSuccess { toast("تم حفظ إعدادات التوصيل"); showDelivery() }
                    .onFailure { toast(it.message ?: "تعذر حفظ التوصيل") }
            }
        }
    }

    private fun showProducts() {
        val sellerId = seller?.id ?: return showError("حساب التاجر غير مكتمل")
        loading("جاري تحميل المنتجات…")
        lifecycleScope.launch {
            runCatching { repository.sellerProducts(sellerId) to repository.categories() }
                .onSuccess { (products, categories) -> renderProducts(sellerId, products, categories) }
                .onFailure { showError(it.message ?: "تعذر تحميل المنتجات") }
        }
    }

    private fun renderProducts(sellerId: String, products: List<Product>, categories: List<Category>) {
        clear(); backHeader("المنتجات والمخزون") { loadEntry() }
        button("إضافة منتج") { productDialog(null, sellerId, categories) }
        if (products.isEmpty()) text("لا توجد منتجات بعد.")
        products.forEach { product ->
            card().apply {
                addView(textView("${product.name}\n${money(product.price)} جنيه • المخزون ${product.stock}\n${if (product.is_active) "ظاهر" else "متوقف"}", 17f, true))
                addView(row().apply {
                    addView(actionButton("تعديل") { productDialog(product, sellerId, categories) })
                    addView(actionButton(if (product.is_active) "إيقاف" else "نشر") {
                        lifecycleScope.launch {
                            runCatching { repository.deactivateMerchantProduct(product.id, !product.is_active) }
                                .onSuccess { showProducts() }.onFailure { toast(it.message ?: "تعذر تحديث المنتج") }
                        }
                    })
                    addView(actionButton("الصور") { imageManager(product) })
                    addView(actionButton("الخيارات") { variantManager(product) })
                    addView(actionButton("حذف") { confirmDeleteProduct(product) })
                })
            }.also(box::addView)
        }
    }

    private fun productDialog(product: Product?, sellerId: String, categories: List<Category>) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val name = dialogInput("اسم المنتج", product?.name)
        val description = dialogInput("الوصف", product?.description, multiline = true)
        val price = dialogInput("السعر", product?.price?.toString(), decimal = true)
        val stock = dialogInput("المخزون", product?.stock?.toString(), number = true)
        val category = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
            val index = categories.indexOfFirst { it.id == product?.category_id }
            if (index >= 0) setSelection(index)
        }
        val active = CheckBox(requireContext()).apply { text = "ظاهر للعملاء"; isChecked = product?.is_active ?: true }
        listOf(name, description, category, price, stock, active).forEach(container::addView)
        AlertDialog.Builder(requireContext())
            .setTitle(if (product == null) "إضافة منتج" else "تعديل المنتج")
            .setView(container)
            .setPositiveButton("حفظ") { _, _ ->
                val p = price.text.toString().toDoubleOrNull()
                val st = stock.text.toString().toIntOrNull()
                val cat = categories.getOrNull(category.selectedItemPosition)
                if (p == null || st == null) return@setPositiveButton toast("راجعي السعر والمخزون")
                lifecycleScope.launch {
                    runCatching {
                        if (product == null) repository.createMerchantProduct(sellerId, cat?.id, name.text.toString(), description.text.toString(), p, st, active.isChecked)
                        else repository.updateMerchantProduct(product.id, cat?.id, name.text.toString(), description.text.toString(), p, st, active.isChecked)
                    }.onSuccess { showProducts() }.onFailure { toast(it.message ?: "تعذر حفظ المنتج") }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }


    private fun confirmDeleteProduct(product: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle("حذف المنتج")
            .setMessage("سيتم الحذف نهائياً فقط إذا لم يكن للمنتج سجل طلبات. وإلا استخدمي إيقاف المنتج.")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repository.deleteMerchantProduct(product.id) }
                        .onSuccess { toast("تم حذف المنتج"); showProducts() }
                        .onFailure { toast(it.message ?: "تعذر حذف المنتج") }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun imageManager(product: Product) {
        lifecycleScope.launch {
            runCatching { repository.productImages(product.id) }.onSuccess { images ->
                val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 10) }
                val add = actionButton("إضافة صورة") {
                    pendingProductImageId = product.id
                    pickProductImage.launch("image/*")
                }
                content.addView(add)
                if (images.isEmpty()) content.addView(textView("لا توجد صور"))
                images.forEach { image ->
                    content.addView(row().apply {
                        addView(textView(if (image.is_primary) "الصورة الرئيسية" else "صورة المنتج", 15f, image.is_primary), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(actionButton("حذف") {
                            lifecycleScope.launch {
                                runCatching { repository.deleteProductImage(image) }
                                    .onSuccess { toast("تم حذف الصورة") }
                                    .onFailure { toast(it.message ?: "تعذر حذف الصورة") }
                            }
                        })
                    })
                }
                AlertDialog.Builder(requireContext()).setTitle("صور ${product.name}").setView(content).setPositiveButton("إغلاق", null).show()
            }.onFailure { toast(it.message ?: "تعذر تحميل الصور") }
        }
    }

    private fun uploadProductImage(uri: Uri, productId: String) {
        lifecycleScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 84) }
                val path = repository.uploadProductImage(bytes, productId)
                val current = repository.productImages(productId)
                repository.attachProductImage(productId, path, primary = current.isEmpty())
            }.onSuccess { toast("تمت إضافة صورة المنتج"); showProducts() }
                .onFailure { toast(it.message ?: "تعذر رفع الصورة") }
        }
    }

    private fun variantManager(product: Product) {
        lifecycleScope.launch {
            runCatching { repository.productVariants(product.id) }.onSuccess { variants -> renderVariantDialog(product, variants) }
                .onFailure { toast(it.message ?: "تعذر تحميل الخيارات") }
        }
    }

    private fun renderVariantDialog(product: Product, variants: List<ProductVariant>) {
        val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 10) }
        content.addView(actionButton("إضافة خيار / Variant") { addVariantDialog(product) })
        if (variants.isEmpty()) content.addView(textView("لا توجد خيارات"))
        variants.forEach { v ->
            content.addView(row().apply {
                addView(textView("${v.name} • مخزون ${v.stock}${v.price?.let { " • ${money(it)}" } ?: ""}", 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(actionButton("تعديل") { editVariantDialog(v, product) })
                addView(actionButton("حذف") {
                    lifecycleScope.launch {
                        runCatching { repository.deleteProductVariant(v.id) }
                            .onSuccess { toast("تم حذف الخيار") }
                            .onFailure { toast(it.message ?: "تعذر حذف الخيار") }
                    }
                })
            })
        }
        AlertDialog.Builder(requireContext()).setTitle("خيارات ${product.name}").setView(content).setPositiveButton("إغلاق", null).show()
    }

    private fun addVariantDialog(product: Product) = variantEditDialog(null, product)
    private fun editVariantDialog(variant: ProductVariant, product: Product) = variantEditDialog(variant, product)

    private fun variantEditDialog(variant: ProductVariant?, product: Product) {
        val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 8) }
        val name = dialogInput("اسم الخيار (مثلاً: كبير / أحمر)", variant?.name)
        val sku = dialogInput("SKU اختياري", variant?.sku)
        val price = dialogInput("سعر خاص اختياري", variant?.price?.toString(), decimal = true)
        val stock = dialogInput("المخزون", variant?.stock?.toString() ?: "0", number = true)
        val active = CheckBox(requireContext()).apply { text = "الخيار متاح"; isChecked = variant?.is_active ?: true }
        listOf(name, sku, price, stock, active).forEach(content::addView)
        AlertDialog.Builder(requireContext()).setTitle(if (variant == null) "إضافة خيار" else "تعديل الخيار").setView(content)
            .setPositiveButton("حفظ") { _, _ ->
                val st = stock.text.toString().toIntOrNull() ?: return@setPositiveButton toast("المخزون غير صحيح")
                val pr = price.text.toString().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                lifecycleScope.launch {
                    runCatching {
                        if (variant == null) repository.addProductVariant(product.id, name.text.toString(), sku.text.toString(), pr, st)
                        else repository.updateProductVariant(variant.id, name.text.toString(), sku.text.toString(), pr, st, active.isChecked)
                    }.onSuccess { toast("تم حفظ الخيار") }.onFailure { toast(it.message ?: "تعذر حفظ الخيار") }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showOrders() {
        val sellerId = seller?.id ?: return showError("حساب التاجر غير مكتمل")
        loading("جاري تحميل الطلبات…")
        lifecycleScope.launch {
            runCatching { repository.merchantOrders(sellerId) }
                .onSuccess { renderOrders(it) }
                .onFailure { showError(it.message ?: "تعذر تحميل الطلبات") }
        }
    }

    private fun renderOrders(orders: List<Order>) {
        clear(); backHeader("طلبات التاجر") { loadEntry() }
        if (orders.isEmpty()) { text("لا توجد طلبات حتى الآن."); return }
        orders.forEach { order ->
            card().apply {
                addView(textView("طلب #${order.id.take(8)} • ${statusText(order.status)}", 17f, true))
                addView(textView("العميل: ${order.customer_name_snapshot ?: "—"}\nالهاتف: ${order.phone}\nالعنوان: ${order.address}\nالإجمالي: ${money(order.total)} جنيه\nملاحظات: ${order.customer_note ?: "—"}"))
                addView(actionButton("عرض المنتجات") { showMerchantOrderItems(order) })
                val actions = nextOrderActions(order.status)
                if (actions.isNotEmpty()) addView(row().apply {
                    actions.forEach { (label, status) -> addView(actionButton(label) { transitionOrder(order, status) }) }
                })
            }.also(box::addView)
        }
    }

    private fun showMerchantOrderItems(order: Order) {
        lifecycleScope.launch {
            runCatching { repository.merchantOrderItems(order.id) }.onSuccess { items ->
                val message = if (items.isEmpty()) "لا توجد منتجات" else items.joinToString("\n") {
                    "• ${it.product_name_snapshot ?: it.product_id.take(8)} × ${it.quantity} — ${money(it.line_total ?: it.unit_price * it.quantity)} جنيه"
                }
                AlertDialog.Builder(requireContext()).setTitle("منتجات الطلب").setMessage(message).setPositiveButton("إغلاق", null).show()
            }.onFailure { toast(it.message ?: "تعذر تحميل المنتجات") }
        }
    }

    private fun nextOrderActions(status: String): List<Pair<String, String>> = when (status) {
        "pending" -> listOf("قبول" to "accepted", "رفض" to "rejected")
        "accepted" -> listOf("بدء التجهيز" to "preparing", "إلغاء" to "cancelled")
        "preparing" -> listOf("جاهز" to "ready", "إلغاء" to "cancelled")
        "ready" -> listOf("خرج للتوصيل" to "out_for_delivery", "إلغاء" to "cancelled")
        "out_for_delivery" -> listOf("تم التسليم" to "delivered", "فشل التسليم" to "failed")
        else -> emptyList()
    }

    private fun transitionOrder(order: Order, status: String) {
        AlertDialog.Builder(requireContext()).setTitle("تأكيد تحديث الطلب")
            .setMessage("تغيير الحالة إلى ${statusText(status)}؟")
            .setPositiveButton("تأكيد") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repository.transitionMerchantOrder(order.id, status) }
                        .onSuccess { showOrders() }
                        .onFailure { toast(it.message ?: "تعذر تحديث الطلب") }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showDashboard() {
        loading("جاري حساب المؤشرات…")
        lifecycleScope.launch {
            runCatching { repository.merchantDashboardSummary() }
                .onSuccess(::renderDashboard)
                .onFailure { showError(it.message ?: "تعذر تحميل المؤشرات") }
        }
    }

    private fun renderDashboard(m: MerchantDashboardSummary) {
        clear(); backHeader("لوحة مؤشرات التاجر") { loadEntry() }
        val metrics = listOf(
            "الطلبات" to m.orders.toString(),
            "تم التسليم" to m.delivered_orders.toString(),
            "GMV" to "${money(m.gmv)} جنيه",
            "العملاء" to m.customers.toString(),
            "عملاء متكررون" to m.repeat_customers.toString(),
            "مشاهدات المنتجات" to m.product_views.toString(),
            "Conversion" to "${money(m.conversion_rate)}%",
            "Completion" to "${money(m.completion_rate)}%",
            "Cancellation" to "${money(m.cancellation_rate)}%",
            "التقييم" to money(m.average_rating),
            "المنتجات النشطة" to "${m.active_products}/${m.products}",
            "متوسط الاستجابة" to (m.average_response_seconds?.let { "${(it / 60.0).roundToInt()} دقيقة" } ?: "—")
        )
        metrics.chunked(2).forEach { pair ->
            box.addView(row().apply { pair.forEach { (label, value) -> addView(metricCard(label, value), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) } })
        }
        heading("أفضل المنتجات", 20f)
        if (m.best_sellers.isEmpty()) text("لا توجد بيانات مبيعات بعد.")
        m.best_sellers.forEach { text("${it.name} — ${it.units} وحدة — ${money(it.revenue)} جنيه — ${it.views} مشاهدة") }
    }


    private fun imageBytes(uri: Uri, maxSize: Int, quality: Int): ByteArray {
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("تعذر قراءة الصورة")
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxSize) {
            val ratio = maxSize.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "تعذر تجهيز الصورة" }
            out.toByteArray().also { require(it.size <= 5 * 1024 * 1024) { "الصورة أكبر من 5 ميجابايت" } }
        }
    }

    private fun loading(message: String) { clear(); heading("تاني للتجار"); text(message) }
    private fun showError(message: String) { clear(); heading("تاني للتجار"); text(message, 16f, true); button("إعادة المحاولة") { loadEntry() } }
    private fun clear() { box.removeAllViews() }

    private fun heading(value: String, size: Float = 28f) { box.addView(textView(value, size, true).apply { setPadding(0, 8, 0, 18) }) }
    private fun backHeader(value: String, onBack: () -> Unit) {
        box.addView(row().apply {
            addView(actionButton("رجوع", onBack))
            addView(textView(value, 24f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
    }
    private fun label(value: String) { box.addView(textView(value, 14f, true).apply { setPadding(0, 12, 0, 4) }) }
    private fun text(value: String, size: Float = 15f, bold: Boolean = false): TextView = textView(value, size, bold).also { box.addView(it) }
    private fun textView(value: String, size: Float = 15f, bold: Boolean = false) = TextView(requireContext()).apply {
        text = value; textSize = size; setPadding(4, 6, 4, 6)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun button(value: String, action: () -> Unit) { box.addView(actionButton(value, action), matchWrap()) }
    private fun actionButton(value: String, action: () -> Unit) = Button(requireContext()).apply {
        text = value; isAllCaps = false; setOnClickListener { action() }
    }
    private fun row() = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun card() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14)
        setBackgroundResource(R.drawable.bg_card)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
    }
    private fun metricCard(label: String, value: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14); setBackgroundResource(R.drawable.bg_card)
        addView(textView(label, 13f)); addView(textView(value, 20f, true))
    }
    private fun input(hint: String, value: String? = null, multiline: Boolean = false, number: Boolean = false, numberDecimal: Boolean = false): EditText {
        label(hint)
        return EditText(requireContext()).apply {
            setText(value.orEmpty()); this.hint = hint
            inputType = when {
                numberDecimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                number -> InputType.TYPE_CLASS_NUMBER
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multiline) minLines = 3
            box.addView(this, matchWrap())
        }
    }
    private fun dialogInput(hint: String, value: String? = null, multiline: Boolean = false, number: Boolean = false, decimal: Boolean = false) = EditText(requireContext()).apply {
        setText(value.orEmpty()); this.hint = hint
        inputType = when {
            decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            number -> InputType.TYPE_CLASS_NUMBER
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multiline) minLines = 3
    }
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun money(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    private fun statusText(status: String): String = when (status) {
        "pending" -> "قيد الانتظار"
        "approved" -> "معتمد"
        "changes_requested" -> "تعديلات مطلوبة"
        "rejected" -> "مرفوض"
        "suspended" -> "موقوف"
        "accepted" -> "مقبول"
        "preparing" -> "جاري التجهيز"
        "ready" -> "جاهز"
        "out_for_delivery" -> "خرج للتوصيل"
        "delivered" -> "تم التسليم"
        "cancelled" -> "ملغي"
        "failed" -> "فشل التسليم"
        else -> status
    }
}
