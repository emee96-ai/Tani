package com.tani.app.ui.seller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tani.app.R
import com.tani.app.data.Category
import com.tani.app.data.Product
import com.tani.app.data.ProductVariant
import com.tani.app.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MerchantProductsFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private var pendingImageProductId: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pendingImageProductId
        if (uri != null && id != null) uploadProductImage(uri, id)
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 16), MerchantUi.dp(context, 18), MerchantUi.dp(context, 16), MerchantUi.dp(context, 30))
            MerchantUi.decorateRoot(this)
        }
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) { load() }

    private fun load() {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "المنتجات والمخزون"))
        root.addView(MerchantUi.card(context, soft = true).apply { addView(MerchantUi.text(context, "جاري تحميل المنتجات…", 14f, true)) })
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val seller = repository.seller() ?: error("حساب التاجر غير مكتمل")
                Triple(seller.id, repository.sellerProducts(seller.id), repository.categories())
            }.onSuccess { (sellerId, products, categories) -> render(sellerId, products, categories) }
                .onFailure { showError(it.message ?: "تعذر تحميل المنتجات") }
        }
    }

    private fun render(sellerId: String, products: List<Product>, categories: List<Category>) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "المنتجات والمخزون"))
        root.addView(MerchantUi.subtitle(context, "أضيفي المنتجات، حدّثي المخزون، وأوقفي المنتج مؤقتاً بدل حذفه عند الحاجة."))

        val active = products.count { it.is_active }
        val out = products.count { it.stock <= 0 }
        val low = products.count { it.stock in 1..3 }
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, "كل المنتجات", products.size.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, "المنشورة", active.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, "نفد المخزون", out.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, "مخزون منخفض", low.toString(), "1–3 وحدات"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
        root.addView(MerchantUi.primaryButton(context, "+ إضافة منتج جديد") { productDialog(null, sellerId, categories) })
        root.addView(MerchantUi.sectionTitle(context, "منتجاتك"))

        if (products.isEmpty()) {
            root.addView(MerchantUi.emptyCard(context, "ما عندك منتجات لسه", "أضيفي أول منتج عشان يبدأ متجرك يظهر للعملاء."))
            return
        }

        products.forEach { product ->
            root.addView(MerchantUi.card(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(MerchantUi.text(context, product.name, 16.5f, true))
                        addView(MerchantUi.text(context, "${money(product.price)} جنيه", 15f, true).apply { setPadding(0, MerchantUi.dp(context, 4), 0, 0) })
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(MerchantUi.pill(context, if (!product.is_active) "متوقف" else if (product.stock <= 0) "نفد" else "منشور", when {
                        !product.is_active -> MerchantUi.Tone.NEUTRAL
                        product.stock <= 0 -> MerchantUi.Tone.ERROR
                        product.stock <= 3 -> MerchantUi.Tone.WARNING
                        else -> MerchantUi.Tone.SUCCESS
                    }))
                })
                addView(MerchantUi.muted(context, "المخزون: ${product.stock}", 12.5f).apply { setPadding(0, MerchantUi.dp(context, 7), 0, MerchantUi.dp(context, 8)) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, "تعديل") { productDialog(product, sellerId, categories) })
                    addView(MerchantUi.compactButton(context, if (product.is_active) "إيقاف" else "نشر") { toggle(product) })
                    addView(MerchantUi.compactButton(context, "الصور") { imageManager(product) })
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, "الخيارات والمقاسات") { variantManager(product) })
                    addView(MerchantUi.compactButton(context, "حذف") { confirmDelete(product) })
                }.apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
            })
        }
    }

    private fun productDialog(product: Product?, sellerId: String, categories: List<Category>) {
        val context = requireContext()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(MerchantUi.dp(context, 20), 0, MerchantUi.dp(context, 20), 0) }
        val name = dialogField("اسم المنتج", product?.name)
        val description = dialogField("الوصف", product?.description, multiline = true)
        val category = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
            val index = categories.indexOfFirst { it.id == product?.category_id }
            if (index >= 0) setSelection(index)
        }
        val price = dialogField("السعر", product?.price?.let(::money), decimal = true)
        val stock = dialogField("المخزون", product?.stock?.toString() ?: "0", number = true)
        val visible = CheckBox(context).apply { text = "ظاهر للعملاء"; isChecked = product?.is_active ?: true }
        listOf(name, description, category, price, stock, visible).forEach {
            content.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = MerchantUi.dp(context, 8) })
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(if (product == null) "إضافة منتج" else "تعديل المنتج")
            .setView(content)
            .setPositiveButton("حفظ") { _, _ ->
                val p = price.text.toString().toDoubleOrNull()
                val s = stock.text.toString().toIntOrNull()
                if (name.text.toString().trim().length < 2) return@setPositiveButton toast("اكتبي اسم المنتج")
                if (p == null || p < 0 || s == null || s < 0) return@setPositiveButton toast("راجعي السعر والمخزون")
                val cat = categories.getOrNull(category.selectedItemPosition)
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        if (product == null) repository.createMerchantProduct(sellerId, cat?.id, name.text.toString(), description.text.toString(), p, s, visible.isChecked)
                        else repository.updateMerchantProduct(product.id, cat?.id, name.text.toString(), description.text.toString(), p, s, visible.isChecked)
                    }.onSuccess { toast("تم حفظ المنتج ✓"); load() }
                        .onFailure { toast(it.message ?: "تعذر حفظ المنتج") }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun toggle(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.deactivateMerchantProduct(product.id, !product.is_active) }
                .onSuccess { load() }
                .onFailure { toast(it.message ?: "تعذر تحديث المنتج") }
        }
    }

    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف ${product.name}؟")
            .setMessage("إذا كان للمنتج سجل طلبات لن يُحذف نهائياً. في هذه الحالة استخدمي إيقاف المنتج.")
            .setPositiveButton("حذف") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { repository.deleteMerchantProduct(product.id) }
                        .onSuccess { toast("تم حذف المنتج"); load() }
                        .onFailure { toast(it.message ?: "تعذر حذف المنتج") }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun imageManager(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.productImages(product.id) }.onSuccess { images ->
                val context = requireContext()
                val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0) }
                content.addView(MerchantUi.primaryButton(context, "+ إضافة صورة") {
                    pendingImageProductId = product.id
                    pickImage.launch("image/*")
                })
                if (images.isEmpty()) content.addView(MerchantUi.muted(context, "لا توجد صور لهذا المنتج.").apply { setPadding(0, MerchantUi.dp(context, 10), 0, 0) })
                images.forEach { image ->
                    content.addView(MerchantUi.card(context).apply {
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                            addView(MerchantUi.text(context, if (image.is_primary) "الصورة الرئيسية" else "صورة المنتج", 14f, image.is_primary), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                            addView(MerchantUi.compactButton(context, "حذف") {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    runCatching { repository.deleteProductImage(image) }
                                        .onSuccess { toast("تم حذف الصورة"); imageManager(product) }
                                        .onFailure { toast(it.message ?: "تعذر حذف الصورة") }
                                }
                            })
                        })
                    })
                }
                MaterialAlertDialogBuilder(context).setTitle("صور ${product.name}").setView(content).setPositiveButton("إغلاق", null).show()
            }.onFailure { toast(it.message ?: "تعذر تحميل الصور") }
        }
    }

    private fun uploadProductImage(uri: Uri, productId: String) {
        toast("جاري رفع الصورة…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 84) }
                val path = repository.uploadProductImage(bytes, productId)
                val current = repository.productImages(productId)
                repository.attachProductImage(productId, path, primary = current.isEmpty())
            }.onSuccess { toast("تمت إضافة الصورة ✓"); load() }
                .onFailure { toast(it.message ?: "تعذر رفع الصورة") }
        }
    }

    private fun variantManager(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.productVariants(product.id) }
                .onSuccess { variants -> renderVariants(product, variants) }
                .onFailure { toast(it.message ?: "تعذر تحميل الخيارات") }
        }
    }

    private fun renderVariants(product: Product, variants: List<ProductVariant>) {
        val context = requireContext()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0) }
        content.addView(MerchantUi.primaryButton(context, "+ إضافة خيار / مقاس") { variantDialog(null, product) })
        if (variants.isEmpty()) content.addView(MerchantUi.muted(context, "لا توجد خيارات بعد.").apply { setPadding(0, MerchantUi.dp(context, 10), 0, 0) })
        variants.forEach { variant ->
            content.addView(MerchantUi.card(context).apply {
                addView(MerchantUi.text(context, variant.name, 14.5f, true))
                addView(MerchantUi.muted(context, "المخزون ${variant.stock}${variant.price?.let { " • ${money(it)} جنيه" } ?: ""}", 12f).apply { setPadding(0, MerchantUi.dp(context, 4), 0, MerchantUi.dp(context, 6)) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, "تعديل") { variantDialog(variant, product) })
                    addView(MerchantUi.compactButton(context, "حذف") {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { repository.deleteProductVariant(variant.id) }
                                .onSuccess { toast("تم حذف الخيار"); variantManager(product) }
                                .onFailure { toast(it.message ?: "تعذر حذف الخيار") }
                        }
                    })
                })
            })
        }
        MaterialAlertDialogBuilder(context).setTitle("خيارات ${product.name}").setView(content).setPositiveButton("إغلاق", null).show()
    }

    private fun variantDialog(variant: ProductVariant?, product: Product) {
        val context = requireContext()
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(MerchantUi.dp(context, 20), 0, MerchantUi.dp(context, 20), 0) }
        val name = dialogField("الخيار: مثلاً أحمر / كبير", variant?.name)
        val sku = dialogField("SKU اختياري", variant?.sku)
        val price = dialogField("سعر خاص اختياري", variant?.price?.let(::money), decimal = true)
        val stock = dialogField("المخزون", variant?.stock?.toString() ?: "0", number = true)
        val active = CheckBox(context).apply { text = "الخيار متاح"; isChecked = variant?.is_active ?: true }
        listOf(name, sku, price, stock, active).forEach { content.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = MerchantUi.dp(context, 8) }) }
        MaterialAlertDialogBuilder(context).setTitle(if (variant == null) "إضافة خيار" else "تعديل الخيار").setView(content)
            .setPositiveButton("حفظ") { _, _ ->
                val st = stock.text.toString().toIntOrNull() ?: return@setPositiveButton toast("المخزون غير صحيح")
                val pr = price.text.toString().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                if (name.text.toString().trim().isEmpty()) return@setPositiveButton toast("اسم الخيار مطلوب")
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        if (variant == null) repository.addProductVariant(product.id, name.text.toString(), sku.text.toString(), pr, st)
                        else repository.updateProductVariant(variant.id, name.text.toString(), sku.text.toString(), pr, st, active.isChecked)
                    }.onSuccess { toast("تم حفظ الخيار"); variantManager(product) }
                        .onFailure { toast(it.message ?: "تعذر حفظ الخيار") }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun dialogField(hint: String, value: String?, multiline: Boolean = false, number: Boolean = false, decimal: Boolean = false) = EditText(requireContext()).apply {
        setText(value.orEmpty()); this.hint = hint; setBackgroundResource(R.drawable.bg_field)
        setPadding(MerchantUi.dp(context, 12), MerchantUi.dp(context, 10), MerchantUi.dp(context, 12), MerchantUi.dp(context, 10))
        inputType = when {
            decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            number -> InputType.TYPE_CLASS_NUMBER
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multiline) minLines = 3
    }

    private fun imageBytes(uri: Uri, maxSize: Int, quality: Int): ByteArray {
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("تعذر قراءة الصورة")
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

    private fun showError(message: String) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "المنتجات والمخزون"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل المنتجات", 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }
    private fun money(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()
}
