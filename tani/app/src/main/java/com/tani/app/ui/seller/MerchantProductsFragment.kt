package com.tani.app.ui.seller

import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tani.app.R
import com.tani.app.data.*
import com.tani.app.data.network.CustomerErrorMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MerchantProductsFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private var pendingImageProduct: Product? = null
    private val pendingCreateImages = mutableListOf<Uri>()
    private var pendingCreateImageStatus: TextView? = null
    private val busyProductIds = mutableSetOf<String>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val product = pendingImageProduct
        if (uri != null && product != null) {
            uploadProductImage(uri, product)
        } else {
            pendingImageProduct = null
        }
    }

    private val pickCreateImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            pendingCreateImages.clear()
            pendingCreateImages.addAll(uris.distinct().take(MAX_PRODUCT_IMAGES))
            if (uris.distinct().size > MAX_PRODUCT_IMAGES && isAdded) {
                toast("الحد الأقصى $MAX_PRODUCT_IMAGES صور للمنتج")
            }
            updateCreateImageStatus()
        }
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
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "المنتجات والمخزون"))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, "جاري تحميل المنتجات…", 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val seller = repository.seller() ?: error("حساب التاجر غير مكتمل")
                coroutineScope {
                    val categoriesRequest = async { runCatching { repository.categories() }.getOrDefault(emptyList()) }
                    val products = repository.sellerProducts(seller.id)
                    val variantProducts = products.filter { it.has_variants }.map { it.id }
                    val variants = runCatching {
                        repository.merchantProductVariantsForProducts(variantProducts)
                    }.getOrDefault(emptyList())
                    CatalogData(seller.id, products, categoriesRequest.await(), variants)
                }
            }.onSuccess(::render)
                .onFailure { error ->
                    showError(CustomerErrorMessages.from(error, "تعذر تحميل المنتجات. حاولي مرة أخرى."))
                }
        }
    }

    private fun render(data: CatalogData) {
        val context = requireContext()
        val products = data.products
        val variantsByProduct = data.variants.groupBy { it.product_id }
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "المنتجات والمخزون"))
        root.addView(MerchantUi.subtitle(context, "أضيفي المنتجات، حدّثي المخزون، وأوقفي المنتج مؤقتاً بدل حذفه عند الحاجة."))

        if (data.categories.isEmpty()) {
            root.addView(MerchantUi.card(context, soft = true).apply {
                addView(MerchantUi.text(context, "الفئات غير متاحة الآن", 14f, true))
                addView(MerchantUi.muted(context, "تقدري تديري منتجاتك الحالية، لكن تغيير الفئة قد يكون محدوداً لحد ما ترجع الشبكة.", 12.5f).apply {
                    setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                })
            })
        }

        val active = products.count { it.is_active }
        val out = products.count { effectiveStock(it, variantsByProduct) <= 0 }
        val low = products.count { effectiveStock(it, variantsByProduct) in 1..LOW_STOCK_THRESHOLD }
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, "كل المنتجات", products.size.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, "المنشورة", active.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, "نفد المخزون", out.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, "مخزون منخفض", low.toString(), "1–$LOW_STOCK_THRESHOLD وحدات"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
        root.addView(MerchantUi.primaryButton(context, "+ إضافة منتج جديد") {
            productDialog(null, data.sellerId, data.categories)
        })
        root.addView(MerchantUi.sectionTitle(context, "منتجاتك"))

        if (products.isEmpty()) {
            root.addView(MerchantUi.emptyCard(context, "ما عندك منتجات لسه", "أضيفي أول منتج عشان يبدأ متجرك يظهر للعملاء."))
            return
        }

        products.forEach { product ->
            val productVariants = variantsByProduct[product.id].orEmpty()
            val activeVariants = productVariants.filter { it.is_active }
            val availableStock = effectiveStock(product, variantsByProduct)
            val statusText = when {
                !product.is_active -> "متوقف"
                product.has_variants && activeVariants.isEmpty() -> "بدون خيارات"
                availableStock <= 0 -> "نفد"
                availableStock <= LOW_STOCK_THRESHOLD -> "مخزون منخفض"
                else -> "منشور"
            }
            val statusTone = when {
                !product.is_active -> MerchantUi.Tone.NEUTRAL
                product.has_variants && activeVariants.isEmpty() -> MerchantUi.Tone.ERROR
                availableStock <= 0 -> MerchantUi.Tone.ERROR
                availableStock <= LOW_STOCK_THRESHOLD -> MerchantUi.Tone.WARNING
                else -> MerchantUi.Tone.SUCCESS
            }

            root.addView(MerchantUi.card(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(MerchantUi.text(context, product.name, 16.5f, true))
                        addView(MerchantUi.text(context, "${money(product.price)} جنيه", 15f, true).apply {
                            setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                        })
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(MerchantUi.pill(context, statusText, statusTone))
                })

                val stockLabel = if (product.has_variants) {
                    "المخزون المتاح: $availableStock عبر ${activeVariants.size} خيارات نشطة"
                } else {
                    "المخزون: ${product.stock}"
                }
                addView(MerchantUi.muted(context, stockLabel, 12.5f).apply {
                    setPadding(0, MerchantUi.dp(context, 7), 0, MerchantUi.dp(context, 8))
                })

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, "تعديل") { productDialog(product, data.sellerId, data.categories) })
                    addView(MerchantUi.compactButton(context, if (product.is_active) "إيقاف" else "نشر") { toggle(product) })
                    addView(MerchantUi.compactButton(context, "الصور") { imageManager(product) })
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, "الخيارات والمقاسات") { variantManager(product) })
                    addView(MerchantUi.compactButton(context, "حذف") { confirmDelete(product) })
                }.apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
            })
        }
    }

    private fun effectiveStock(product: Product, variantsByProduct: Map<String, List<ProductVariant>>): Int {
        if (!product.has_variants) return product.stock
        return variantsByProduct[product.id].orEmpty()
            .filter { it.is_active }
            .sumOf { it.stock.coerceAtLeast(0) }
    }

    private fun productDialog(product: Product?, sellerId: String, categories: List<Category>) {
        val context = requireContext()
        val isCreating = product == null
        if (isCreating) {
            pendingCreateImages.clear()
            pendingCreateImageStatus = null
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 20), 0, MerchantUi.dp(context, 20), 0)
        }

        if (isCreating) {
            content.addView(MerchantUi.text(context, "صور المنتج *", 14f, true))
            content.addView(MerchantUi.secondaryButton(context, "اختيار صور المنتج") {
                pickCreateImages.launch("image/*")
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = MerchantUi.dp(context, 6)
                bottomMargin = MerchantUi.dp(context, 6)
            })
            pendingCreateImageStatus = MerchantUi.muted(context, "اختاري صورة واحدة على الأقل قبل حفظ المنتج.", 12.5f).also {
                content.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = MerchantUi.dp(context, 12)
                })
            }
        }

        val name = dialogField("اسم المنتج", product?.name)
        val description = dialogField("الوصف", product?.description, multiline = true)
        val category = if (categories.isNotEmpty()) {
            Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
                val index = categories.indexOfFirst { it.id == product?.category_id }
                if (index >= 0) setSelection(index)
            }
        } else null
        val price = dialogField("السعر", product?.price?.let(::money), decimal = true)
        val stock = dialogField("المخزون", product?.stock?.toString() ?: "0", number = true)
        val visible = CheckBox(context).apply {
            text = "ظاهر للعملاء"
            isChecked = product?.is_active ?: true
        }

        content.addView(name, fieldParams())
        content.addView(description, fieldParams())
        if (category != null) {
            content.addView(category, fieldParams())
        } else {
            content.addView(MerchantUi.muted(context, "الفئات غير متاحة الآن؛ سيتم الاحتفاظ بالفئة الحالية إن وجدت.", 12f), fieldParams())
        }
        content.addView(price, fieldParams())
        content.addView(stock, fieldParams())
        content.addView(visible, fieldParams())

        if (product?.has_variants == true) {
            content.addView(MerchantUi.muted(context, "ملاحظة: مخزون المنتج الظاهر للزبون يُحسب من مجموع الخيارات النشطة، وليس من رقم المخزون الأساسي هنا.", 12f).apply {
                setPadding(0, 0, 0, MerchantUi.dp(context, 8))
            })
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (isCreating) "إضافة منتج" else "تعديل المنتج")
            .setView(content)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnDismissListener {
            if (isCreating) {
                pendingCreateImages.clear()
                pendingCreateImageStatus = null
            }
        }

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val cleanName = name.text.toString().trim()
                val cleanDescription = description.text.toString().trim()
                val p = price.text.toString().toDoubleOrNull()
                val s = stock.text.toString().toIntOrNull()
                if (cleanName.length < 2) {
                    toast("اكتبي اسم المنتج")
                    return@setOnClickListener
                }
                if (p == null || p < 0 || s == null || s < 0) {
                    toast("راجعي السعر والمخزون")
                    return@setOnClickListener
                }
                val selectedImages = pendingCreateImages.toList()
                if (isCreating && selectedImages.isEmpty()) {
                    toast("اختاري صورة واحدة على الأقل للمنتج")
                    return@setOnClickListener
                }

                val categoryId = category?.let { spinner ->
                    categories.getOrNull(spinner.selectedItemPosition)?.id
                } ?: product?.category_id

                saveButton.isEnabled = false
                toast(if (isCreating) "جاري حفظ المنتج ورفع الصور…" else "جاري حفظ التعديلات…")

                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        if (isCreating) {
                            val preparedImages = selectedImages.map { uri ->
                                withContext(Dispatchers.IO) { imageBytes(uri, 1600, 84) }
                            }
                            val created = repository.createMerchantProduct(
                                sellerId = sellerId,
                                categoryId = categoryId,
                                name = cleanName,
                                description = cleanDescription,
                                price = p,
                                stock = s,
                                active = false
                            )
                            var uploaded = 0
                            var draftReason: String? = null
                            for ((index, bytes) in preparedImages.withIndex()) {
                                try {
                                    repository.uploadAndAttachMerchantProductImage(
                                        bytes = bytes,
                                        productId = created.id,
                                        primary = index == 0
                                    )
                                    uploaded += 1
                                } catch (error: Throwable) {
                                    draftReason = CustomerErrorMessages.from(error, "تعذر إكمال رفع الصور")
                                    break
                                }
                            }
                            if (draftReason == null && visible.isChecked) {
                                try {
                                    repository.deactivateMerchantProduct(created.id, true)
                                } catch (error: Throwable) {
                                    draftReason = CustomerErrorMessages.from(error, "تعذر نشر المنتج الآن")
                                }
                            }
                            SaveOutcome(
                                savedAsDraft = draftReason != null,
                                uploadedImages = uploaded,
                                totalImages = preparedImages.size,
                                detail = draftReason
                            )
                        } else {
                            repository.updateMerchantProduct(
                                product.id,
                                categoryId,
                                cleanName,
                                cleanDescription,
                                p,
                                s,
                                visible.isChecked
                            )
                            SaveOutcome()
                        }
                    }.onSuccess { outcome ->
                        if (outcome.savedAsDraft) {
                            toast("تم حفظ المنتج كمسودة. اكتمل رفع ${outcome.uploadedImages} من ${outcome.totalImages} صور. ${outcome.detail.orEmpty()}")
                        } else {
                            toast(if (isCreating) "تمت إضافة المنتج وصوره ✓" else "تم حفظ المنتج ✓")
                        }
                        dialog.dismiss()
                        load()
                    }.onFailure { error ->
                        saveButton.isEnabled = true
                        toast(CustomerErrorMessages.from(error, if (isCreating) "تعذر حفظ المنتج" else "تعذر حفظ التعديلات"))
                    }
                }
            }
        }
        dialog.show()
    }

    private fun fieldParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = MerchantUi.dp(requireContext(), 8) }

    private fun updateCreateImageStatus() {
        val count = pendingCreateImages.size
        pendingCreateImageStatus?.text = if (count == 0) {
            "اختاري صورة واحدة على الأقل قبل حفظ المنتج."
        } else {
            "تم اختيار $count ${if (count == 1) "صورة" else "صور"}. أول صورة ستكون الرئيسية."
        }
    }

    private fun toggle(product: Product) {
        if (!busyProductIds.add(product.id)) return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                if (!product.is_active) {
                    val imagesRequest = async { repository.productImages(product.id) }
                    val variantsRequest = async { repository.merchantProductVariants(product.id) }
                    val images = imagesRequest.await()
                    val variants = variantsRequest.await()
                    require(images.isNotEmpty()) { "أضيفي صورة واحدة على الأقل قبل نشر المنتج" }
                    if (product.has_variants || variants.isNotEmpty()) {
                        require(variants.any { it.is_active }) { "فعّلي خياراً واحداً على الأقل قبل نشر المنتج" }
                    }
                }
                repository.deactivateMerchantProduct(product.id, !product.is_active)
            }.onSuccess {
                toast(if (product.is_active) "تم إيقاف المنتج" else "تم نشر المنتج ✓")
                load()
            }.onFailure { error ->
                toast(CustomerErrorMessages.from(error, "تعذر تحديث حالة المنتج"))
            }
            busyProductIds.remove(product.id)
        }
    }

    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("حذف ${product.name}؟")
            .setMessage("إذا كان للمنتج سجل طلبات لن يُحذف نهائياً. في هذه الحالة استخدمي إيقاف المنتج.")
            .setPositiveButton("حذف") { _, _ ->
                if (!busyProductIds.add(product.id)) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { repository.deleteMerchantProduct(product.id) }
                        .onSuccess {
                            toast("تم حذف المنتج")
                            load()
                        }
                        .onFailure { error ->
                            toast(CustomerErrorMessages.from(error, "تعذر حذف المنتج. أوقفيه بدلاً من الحذف إذا كان مرتبطاً بطلبات."))
                        }
                    busyProductIds.remove(product.id)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun imageManager(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.productImages(product.id) }
                .onSuccess { images ->
                    val context = requireContext()
                    val content = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
                    }
                    var managerDialog: androidx.appcompat.app.AlertDialog? = null

                    if (images.size < MAX_PRODUCT_IMAGES) {
                        content.addView(MerchantUi.primaryButton(context, "+ إضافة صورة") {
                            pendingImageProduct = product
                            managerDialog?.dismiss()
                            pickImage.launch("image/*")
                        })
                    } else {
                        content.addView(MerchantUi.muted(context, "وصلتي للحد الأقصى: $MAX_PRODUCT_IMAGES صور.", 12.5f))
                    }

                    if (images.isEmpty()) {
                        content.addView(MerchantUi.muted(context, "لا توجد صور لهذا المنتج.").apply {
                            setPadding(0, MerchantUi.dp(context, 10), 0, 0)
                        })
                    }

                    images.forEach { image ->
                        content.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, if (image.is_primary) "الصورة الرئيسية" else "صورة المنتج", 14f, image.is_primary), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                if (!image.is_primary) {
                                    addView(MerchantUi.compactButton(context, "اجعلها الرئيسية") {
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            runCatching { repository.setPrimaryProductImage(product.id, image.id) }
                                                .onSuccess {
                                                    toast("تم تغيير الصورة الرئيسية ✓")
                                                    managerDialog?.dismiss()
                                                    imageManager(product)
                                                }
                                                .onFailure { error -> toast(CustomerErrorMessages.from(error, "تعذر تغيير الصورة الرئيسية")) }
                                        }
                                    })
                                }
                            })
                            addView(MerchantUi.compactButton(context, "حذف الصورة") {
                                if (images.size == 1 && product.is_active) {
                                    toast("لا يمكن حذف آخر صورة من منتج منشور. أوقفي المنتج أولاً.")
                                    return@compactButton
                                }
                                viewLifecycleOwner.lifecycleScope.launch {
                                    runCatching {
                                        repository.deleteProductImage(image)
                                        if (image.is_primary) {
                                            val remaining = images.firstOrNull { it.id != image.id }
                                            if (remaining != null) repository.setPrimaryProductImage(product.id, remaining.id)
                                        }
                                    }.onSuccess {
                                        toast("تم حذف الصورة")
                                        managerDialog?.dismiss()
                                        imageManager(product)
                                    }.onFailure { error -> toast(CustomerErrorMessages.from(error, "تعذر حذف الصورة")) }
                                }
                            })
                        })
                    }

                    managerDialog = MaterialAlertDialogBuilder(context)
                        .setTitle("صور ${product.name}")
                        .setView(content)
                        .setPositiveButton("إغلاق", null)
                        .show()
                }
                .onFailure { error -> toast(CustomerErrorMessages.from(error, "تعذر تحميل الصور")) }
        }
    }

    private fun uploadProductImage(uri: Uri, product: Product) {
        toast("جاري رفع الصورة…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val current = repository.productImages(product.id)
                require(current.size < MAX_PRODUCT_IMAGES) { "الحد الأقصى $MAX_PRODUCT_IMAGES صور للمنتج" }
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 84) }
                repository.uploadAndAttachMerchantProductImage(
                    bytes = bytes,
                    productId = product.id,
                    primary = current.isEmpty()
                )
            }.onSuccess {
                pendingImageProduct = null
                toast("تمت إضافة الصورة ✓")
                imageManager(product)
            }.onFailure { error ->
                pendingImageProduct = null
                toast(CustomerErrorMessages.from(error, "تعذر رفع الصورة"))
            }
        }
    }

    private fun variantManager(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.merchantProductVariants(product.id) }
                .onSuccess { variants -> renderVariants(product, variants) }
                .onFailure { error -> toast(CustomerErrorMessages.from(error, "تعذر تحميل الخيارات")) }
        }
    }

    private fun renderVariants(product: Product, variants: List<ProductVariant>) {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
        }
        var managerDialog: androidx.appcompat.app.AlertDialog? = null

        content.addView(MerchantUi.primaryButton(context, "+ إضافة خيار / مقاس") {
            managerDialog?.dismiss()
            variantDialog(null, product)
        })
        if (variants.isEmpty()) {
            content.addView(MerchantUi.muted(context, "لا توجد خيارات بعد.").apply {
                setPadding(0, MerchantUi.dp(context, 10), 0, 0)
            })
        }

        variants.forEach { variant ->
            content.addView(MerchantUi.card(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.text(context, variant.name, 14.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(MerchantUi.pill(context, if (variant.is_active) "متاح" else "متوقف", if (variant.is_active) MerchantUi.Tone.SUCCESS else MerchantUi.Tone.NEUTRAL))
                })
                addView(MerchantUi.muted(context, "المخزون ${variant.stock}${variant.price?.let { " • ${money(it)} جنيه" } ?: ""}", 12f).apply {
                    setPadding(0, MerchantUi.dp(context, 4), 0, MerchantUi.dp(context, 6))
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, "تعديل") {
                        managerDialog?.dismiss()
                        variantDialog(variant, product)
                    })
                    addView(MerchantUi.compactButton(context, "حذف") {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { repository.deleteProductVariant(variant.id) }
                                .onSuccess {
                                    toast("تم حذف الخيار")
                                    managerDialog?.dismiss()
                                    variantManager(product)
                                    load()
                                }
                                .onFailure { error -> toast(CustomerErrorMessages.from(error, "تعذر حذف الخيار")) }
                        }
                    })
                })
            })
        }

        managerDialog = MaterialAlertDialogBuilder(context)
            .setTitle("خيارات ${product.name}")
            .setView(content)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun variantDialog(variant: ProductVariant?, product: Product) {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 20), 0, MerchantUi.dp(context, 20), 0)
        }
        val name = dialogField("الخيار: مثلاً أحمر / كبير", variant?.name)
        val sku = dialogField("SKU اختياري", variant?.sku)
        val price = dialogField("سعر خاص اختياري", variant?.price?.let(::money), decimal = true)
        val stock = dialogField("المخزون", variant?.stock?.toString() ?: "0", number = true)
        val active = CheckBox(context).apply {
            text = "الخيار متاح"
            isChecked = variant?.is_active ?: true
        }
        listOf(name, sku, price, stock, active).forEach { content.addView(it, fieldParams()) }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (variant == null) "إضافة خيار" else "تعديل الخيار")
            .setView(content)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            save.setOnClickListener {
                val cleanName = name.text.toString().trim()
                val cleanSku = sku.text.toString().trim()
                val stockValue = stock.text.toString().toIntOrNull()
                val priceText = price.text.toString().trim()
                val priceValue = priceText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                when {
                    cleanName.isEmpty() -> return@setOnClickListener toast("اسم الخيار مطلوب")
                    stockValue == null || stockValue < 0 -> return@setOnClickListener toast("المخزون غير صحيح")
                    priceText.isNotEmpty() && (priceValue == null || priceValue < 0) -> return@setOnClickListener toast("السعر الخاص غير صحيح")
                }

                save.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        if (variant == null) {
                            val created = repository.addProductVariant(product.id, cleanName, cleanSku, priceValue, stockValue)
                            if (!active.isChecked) {
                                repository.updateProductVariant(created.id, cleanName, cleanSku, priceValue, stockValue, false)
                            }
                        } else {
                            repository.updateProductVariant(variant.id, cleanName, cleanSku, priceValue, stockValue, active.isChecked)
                        }
                    }.onSuccess {
                        toast("تم حفظ الخيار ✓")
                        dialog.dismiss()
                        variantManager(product)
                        load()
                    }.onFailure { error ->
                        save.isEnabled = true
                        toast(CustomerErrorMessages.from(error, "تعذر حفظ الخيار"))
                    }
                }
            }
        }
        dialog.show()
    }

    private fun dialogField(hint: String, value: String?, multiline: Boolean = false, number: Boolean = false, decimal: Boolean = false) = EditText(requireContext()).apply {
        setText(value.orEmpty())
        this.hint = hint
        setBackgroundResource(R.drawable.bg_field)
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
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("تعذر قراءة الصورة")
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxSize) {
            val ratio = maxSize.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "تعذر تجهيز الصورة" }
            out.toByteArray().also {
                require(it.size <= 5 * 1024 * 1024) { "الصورة أكبر من 5 ميجابايت" }
            }
        }
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "المنتجات والمخزون"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل المنتجات", 16f, true))
            addView(MerchantUi.muted(context, message).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10))
            })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }

    private fun money(value: Double) = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value)
    }

    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()

    private data class CatalogData(
        val sellerId: String,
        val products: List<Product>,
        val categories: List<Category>,
        val variants: List<ProductVariant>
    )

    private data class SaveOutcome(
        val savedAsDraft: Boolean = false,
        val uploadedImages: Int = 0,
        val totalImages: Int = 0,
        val detail: String? = null
    )

    companion object {
        private const val MAX_PRODUCT_IMAGES = 8
        private const val LOW_STOCK_THRESHOLD = 3
    }
}
