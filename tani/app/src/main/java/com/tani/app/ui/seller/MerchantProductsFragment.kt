package com.tani.app.ui.seller

import androidx.core.graphics.scale
import com.tani.app.util.decodeBoundedBitmap
import com.tani.app.util.runCatchingCancellable

import android.content.DialogInterface
import android.graphics.Bitmap
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
        root.addView(MerchantUi.title(context, getString(R.string.merchant_products_1)))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_products_2), 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val seller = repository.seller() ?: error(getString(R.string.merchant_delivery_2))
                coroutineScope {
                    val categoriesRequest = async { runCatchingCancellable { repository.categories() }.getOrDefault(emptyList()) }
                    val products = repository.sellerProducts(seller.id)
                    val variantProducts = products.filter { it.has_variants }.map { it.id }
                    val variants = runCatchingCancellable {
                        repository.merchantProductVariantsForProducts(variantProducts)
                    }.getOrDefault(emptyList())
                    CatalogData(seller.id, products, categoriesRequest.await(), variants)
                }
            }.onSuccess(::render)
                .onFailure { error ->
                    showError(CustomerErrorMessages.from(error, getString(R.string.products_18)))
                }
        }
    }

    private fun render(data: CatalogData) {
        val context = requireContext()
        val products = data.products
        val variantsByProduct = data.variants.groupBy { it.product_id }
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_products_1)))
        root.addView(MerchantUi.subtitle(context, getString(R.string.merchant_products_3)))

        if (data.categories.isEmpty()) {
            root.addView(MerchantUi.card(context, soft = true).apply {
                addView(MerchantUi.text(context, getString(R.string.merchant_products_4), 14f, true))
                addView(MerchantUi.muted(context, getString(R.string.merchant_products_5), 12f).apply {
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
            addView(MerchantUi.metricCard(context, getString(R.string.merchant_products_6), products.size.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, getString(R.string.merchant_products_7), active.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.metricCard(context, getString(R.string.merchant_insights_23), out.toString()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
            addView(MerchantUi.metricCard(context, getString(R.string.merchant_products_8), low.toString(), getString(R.string.merchant_products_9, LOW_STOCK_THRESHOLD.toString())), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        })
        root.addView(MerchantUi.primaryButton(context, getString(R.string.merchant_products_10)) {
            productDialog(null, data.sellerId, data.categories)
        })
        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_products_11)))

        if (products.isEmpty()) {
            root.addView(MerchantUi.emptyCard(context, getString(R.string.merchant_products_12), getString(R.string.merchant_products_13)))
            return
        }

        products.forEach { product ->
            val productVariants = variantsByProduct[product.id].orEmpty()
            val activeVariants = productVariants.filter { it.is_active }
            val availableStock = effectiveStock(product, variantsByProduct)
            val statusText = when {
                !product.is_active -> getString(R.string.merchant_products_14)
                product.has_variants && activeVariants.isEmpty() -> getString(R.string.merchant_products_15)
                availableStock <= 0 -> getString(R.string.merchant_products_16)
                availableStock <= LOW_STOCK_THRESHOLD -> getString(R.string.merchant_products_8)
                else -> getString(R.string.merchant_products_17)
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
                        addView(MerchantUi.text(context, product.name, 16f, true))
                        addView(MerchantUi.text(context, getString(R.string.monetization_8, (money(product.price))), 16f, true).apply {
                            setPadding(0, MerchantUi.dp(context, 4), 0, 0)
                        })
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(MerchantUi.pill(context, statusText, statusTone))
                })

                val stockLabel = if (product.has_variants) {
                    getString(R.string.merchant_products_18, availableStock.toString(), activeVariants.size.toString())
                } else {
                    getString(R.string.merchant_products_19, product.stock.toString())
                }
                addView(MerchantUi.muted(context, stockLabel, 12f).apply {
                    setPadding(0, MerchantUi.dp(context, 7), 0, MerchantUi.dp(context, 8))
                })

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, getString(R.string.addresses_7)) { productDialog(product, data.sellerId, data.categories) })
                    addView(MerchantUi.compactButton(context, if (product.is_active) getString(R.string.merchant_products_20) else getString(R.string.merchant_products_21)) { toggle(product) })
                    addView(MerchantUi.compactButton(context, getString(R.string.merchant_products_22)) { imageManager(product) })
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, getString(R.string.merchant_products_23)) { variantManager(product) })
                    addView(MerchantUi.compactButton(context, getString(R.string.cart_13)) { confirmDelete(product) })
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
            content.addView(MerchantUi.text(context, getString(R.string.merchant_products_24), 14f, true))
            content.addView(MerchantUi.secondaryButton(context, getString(R.string.merchant_products_25)) {
                pickCreateImages.launch("image/*")
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = MerchantUi.dp(context, 6)
                bottomMargin = MerchantUi.dp(context, 6)
            })
            pendingCreateImageStatus = MerchantUi.muted(context, getString(R.string.merchant_products_26), 12f).also {
                content.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = MerchantUi.dp(context, 12)
                })
            }
        }

        val name = dialogField(getString(R.string.merchant_products_27), product?.name)
        val description = dialogField(getString(R.string.merchant_products_28), product?.description, multiline = true)
        val category = if (categories.isNotEmpty()) {
            Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
                val index = categories.indexOfFirst { it.id == product?.category_id }
                if (index >= 0) setSelection(index)
            }
        } else null
        val price = dialogField(getString(R.string.merchant_products_29), product?.price?.let(::money), decimal = true)
        val stock = dialogField(getString(R.string.merchant_insights_19), product?.stock?.toString() ?: "0", number = true)
        val visible = CheckBox(context).apply {
            text = getString(R.string.merchant_products_30)
            isChecked = product?.is_active ?: true
        }

        content.addView(name, fieldParams())
        content.addView(description, fieldParams())
        if (category != null) {
            content.addView(category, fieldParams())
        } else {
            content.addView(MerchantUi.muted(context, getString(R.string.merchant_products_31), 12f), fieldParams())
        }
        content.addView(price, fieldParams())
        content.addView(stock, fieldParams())
        content.addView(visible, fieldParams())

        if (product?.has_variants == true) {
            content.addView(MerchantUi.muted(context, getString(R.string.merchant_products_32), 12f).apply {
                setPadding(0, 0, 0, MerchantUi.dp(context, 8))
            })
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (isCreating) getString(R.string.merchant_products_33) else getString(R.string.merchant_products_34))
            .setView(content)
            .setPositiveButton(getString(R.string.addresses_18), null)
            .setNegativeButton(getString(R.string.addresses_19), null)
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
                    toast(getString(R.string.merchant_products_35))
                    return@setOnClickListener
                }
                if (p == null || p < 0 || s == null || s < 0) {
                    toast(getString(R.string.merchant_products_36))
                    return@setOnClickListener
                }
                val selectedImages = pendingCreateImages.toList()
                if (isCreating && selectedImages.isEmpty()) {
                    toast(getString(R.string.merchant_products_37))
                    return@setOnClickListener
                }

                val categoryId = category?.let { spinner ->
                    categories.getOrNull(spinner.selectedItemPosition)?.id
                } ?: product?.category_id

                saveButton.isEnabled = false
                toast(if (isCreating) getString(R.string.merchant_products_38) else getString(R.string.merchant_products_39))

                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable {
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
                                    draftReason = CustomerErrorMessages.from(error, getString(R.string.merchant_products_40))
                                    break
                                }
                            }
                            if (draftReason == null && visible.isChecked) {
                                try {
                                    repository.deactivateMerchantProduct(created.id, true)
                                } catch (error: Throwable) {
                                    draftReason = CustomerErrorMessages.from(error, getString(R.string.merchant_products_41))
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
                            toast(getString(R.string.merchant_products_42, outcome.uploadedImages.toString(), outcome.totalImages.toString(), outcome.detail.orEmpty()))
                        } else {
                            toast(if (isCreating) getString(R.string.merchant_products_43) else getString(R.string.merchant_products_44))
                        }
                        dialog.dismiss()
                        load()
                    }.onFailure { error ->
                        saveButton.isEnabled = true
                        toast(CustomerErrorMessages.from(error, if (isCreating) getString(R.string.merchant_products_45) else getString(R.string.account_3)))
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
            getString(R.string.merchant_products_26)
        } else {
            "تم اختيار $count ${if (count == 1) "صورة" else "صور"}. أول صورة ستكون الرئيسية."
        }
    }

    private fun toggle(product: Product) {
        if (!busyProductIds.add(product.id)) return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                if (!product.is_active) {
                    val imagesRequest = async { repository.productImages(product.id) }
                    val variantsRequest = async { repository.merchantProductVariants(product.id) }
                    val images = imagesRequest.await()
                    val variants = variantsRequest.await()
                    require(images.isNotEmpty()) { getString(R.string.merchant_products_46) }
                    if (product.has_variants || variants.isNotEmpty()) {
                        require(variants.any { it.is_active }) { getString(R.string.merchant_products_47) }
                    }
                }
                repository.deactivateMerchantProduct(product.id, !product.is_active)
            }.onSuccess {
                toast(if (product.is_active) getString(R.string.merchant_products_48) else getString(R.string.merchant_products_49))
                load()
            }.onFailure { error ->
                toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_50)))
            }
            busyProductIds.remove(product.id)
        }
    }

    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.merchant_products_51, product.name))
            .setMessage(getString(R.string.merchant_products_52))
            .setPositiveButton(getString(R.string.cart_13)) { _, _ ->
                if (!busyProductIds.add(product.id)) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable { repository.deleteMerchantProduct(product.id) }
                        .onSuccess {
                            toast(getString(R.string.merchant_products_53))
                            load()
                        }
                        .onFailure { error ->
                            toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_54)))
                        }
                    busyProductIds.remove(product.id)
                }
            }
            .setNegativeButton(getString(R.string.addresses_19), null)
            .show()
    }

    private fun imageManager(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.productImages(product.id) }
                .onSuccess { images ->
                    val context = requireContext()
                    val content = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
                    }
                    var managerDialog: androidx.appcompat.app.AlertDialog? = null

                    if (images.size < MAX_PRODUCT_IMAGES) {
                        content.addView(MerchantUi.primaryButton(context, getString(R.string.merchant_products_55)) {
                            pendingImageProduct = product
                            managerDialog?.dismiss()
                            pickImage.launch("image/*")
                        })
                    } else {
                        content.addView(MerchantUi.muted(context, getString(R.string.merchant_products_56, MAX_PRODUCT_IMAGES.toString()), 12f))
                    }

                    if (images.isEmpty()) {
                        content.addView(MerchantUi.muted(context, getString(R.string.merchant_products_57)).apply {
                            setPadding(0, MerchantUi.dp(context, 10), 0, 0)
                        })
                    }

                    images.forEach { image ->
                        content.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, if (image.is_primary) getString(R.string.merchant_products_58) else getString(R.string.merchant_products_59), 14f, image.is_primary), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                if (!image.is_primary) {
                                    addView(MerchantUi.compactButton(context, getString(R.string.merchant_products_60)) {
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            runCatchingCancellable { repository.setPrimaryProductImage(product.id, image.id) }
                                                .onSuccess {
                                                    toast(getString(R.string.merchant_products_61))
                                                    managerDialog?.dismiss()
                                                    imageManager(product)
                                                }
                                                .onFailure { error -> toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_62))) }
                                        }
                                    })
                                }
                            })
                            addView(MerchantUi.compactButton(context, getString(R.string.merchant_products_63)) {
                                if (images.size == 1 && product.is_active) {
                                    toast(getString(R.string.merchant_products_64))
                                    return@compactButton
                                }
                                viewLifecycleOwner.lifecycleScope.launch {
                                    runCatchingCancellable {
                                        repository.deleteProductImage(image)
                                        if (image.is_primary) {
                                            val remaining = images.firstOrNull { it.id != image.id }
                                            if (remaining != null) repository.setPrimaryProductImage(product.id, remaining.id)
                                        }
                                    }.onSuccess {
                                        toast(getString(R.string.merchant_products_65))
                                        managerDialog?.dismiss()
                                        imageManager(product)
                                    }.onFailure { error -> toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_66))) }
                                }
                            })
                        })
                    }

                    managerDialog = MaterialAlertDialogBuilder(context)
                        .setTitle(getString(R.string.merchant_products_67, product.name))
                        .setView(content)
                        .setPositiveButton(getString(R.string.merchant_orders_28), null)
                        .show()
                }
                .onFailure { error -> toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_68))) }
        }
    }

    private fun uploadProductImage(uri: Uri, product: Product) {
        toast(getString(R.string.merchant_products_69))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val current = repository.productImages(product.id)
                require(current.size < MAX_PRODUCT_IMAGES) { getString(R.string.merchant_products_70, MAX_PRODUCT_IMAGES.toString()) }
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 84) }
                repository.uploadAndAttachMerchantProductImage(
                    bytes = bytes,
                    productId = product.id,
                    primary = current.isEmpty()
                )
            }.onSuccess {
                pendingImageProduct = null
                toast(getString(R.string.merchant_products_71))
                imageManager(product)
            }.onFailure { error ->
                pendingImageProduct = null
                toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_72)))
            }
        }
    }

    private fun variantManager(product: Product) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.merchantProductVariants(product.id) }
                .onSuccess { variants -> renderVariants(product, variants) }
                .onFailure { error -> toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_73))) }
        }
    }

    private fun renderVariants(product: Product, variants: List<ProductVariant>) {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 18), 0, MerchantUi.dp(context, 18), 0)
        }
        var managerDialog: androidx.appcompat.app.AlertDialog? = null

        content.addView(MerchantUi.primaryButton(context, getString(R.string.merchant_products_74)) {
            managerDialog?.dismiss()
            variantDialog(null, product)
        })
        if (variants.isEmpty()) {
            content.addView(MerchantUi.muted(context, getString(R.string.merchant_products_75)).apply {
                setPadding(0, MerchantUi.dp(context, 10), 0, 0)
            })
        }

        variants.forEach { variant ->
            content.addView(MerchantUi.card(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.text(context, variant.name, 14f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(MerchantUi.pill(context, if (variant.is_active) getString(R.string.merchant_products_76) else getString(R.string.merchant_products_14), if (variant.is_active) MerchantUi.Tone.SUCCESS else MerchantUi.Tone.NEUTRAL))
                })
                addView(MerchantUi.muted(context, "المخزون ${variant.stock}${variant.price?.let { " • ${money(it)} جنيه" } ?: ""}", 12f).apply {
                    setPadding(0, MerchantUi.dp(context, 4), 0, MerchantUi.dp(context, 6))
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(MerchantUi.compactButton(context, getString(R.string.addresses_7)) {
                        managerDialog?.dismiss()
                        variantDialog(variant, product)
                    })
                    addView(MerchantUi.compactButton(context, getString(R.string.cart_13)) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatchingCancellable { repository.deleteProductVariant(variant.id) }
                                .onSuccess {
                                    toast(getString(R.string.merchant_products_77))
                                    managerDialog?.dismiss()
                                    variantManager(product)
                                    load()
                                }
                                .onFailure { error -> toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_78))) }
                        }
                    })
                })
            })
        }

        managerDialog = MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.merchant_products_79, product.name))
            .setView(content)
            .setPositiveButton(getString(R.string.merchant_orders_28), null)
            .show()
    }

    private fun variantDialog(variant: ProductVariant?, product: Product) {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 20), 0, MerchantUi.dp(context, 20), 0)
        }
        val name = dialogField(getString(R.string.merchant_products_80), variant?.name)
        val sku = dialogField(getString(R.string.merchant_products_81), variant?.sku)
        val price = dialogField(getString(R.string.merchant_products_82), variant?.price?.let(::money), decimal = true)
        val stock = dialogField(getString(R.string.merchant_insights_19), variant?.stock?.toString() ?: "0", number = true)
        val active = CheckBox(context).apply {
            text = getString(R.string.merchant_products_83)
            isChecked = variant?.is_active ?: true
        }
        listOf(name, sku, price, stock, active).forEach { content.addView(it, fieldParams()) }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (variant == null) getString(R.string.merchant_products_84) else getString(R.string.merchant_products_85))
            .setView(content)
            .setPositiveButton(getString(R.string.addresses_18), null)
            .setNegativeButton(getString(R.string.addresses_19), null)
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
                    cleanName.isEmpty() -> return@setOnClickListener toast(getString(R.string.merchant_products_86))
                    stockValue == null || stockValue < 0 -> return@setOnClickListener toast(getString(R.string.merchant_products_87))
                    priceText.isNotEmpty() && (priceValue == null || priceValue < 0) -> return@setOnClickListener toast(getString(R.string.merchant_products_88))
                }

                save.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable {
                        if (variant == null) {
                            val created = repository.addProductVariant(product.id, cleanName, cleanSku, priceValue, stockValue)
                            if (!active.isChecked) {
                                repository.updateProductVariant(created.id, cleanName, cleanSku, priceValue, stockValue, false)
                            }
                        } else {
                            repository.updateProductVariant(variant.id, cleanName, cleanSku, priceValue, stockValue, active.isChecked)
                        }
                    }.onSuccess {
                        toast(getString(R.string.merchant_products_89))
                        dialog.dismiss()
                        variantManager(product)
                        load()
                    }.onFailure { error ->
                        save.isEnabled = true
                        toast(CustomerErrorMessages.from(error, getString(R.string.merchant_products_90)))
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
        val bitmap = requireContext().contentResolver.decodeBoundedBitmap(uri, maxSize)
            ?: error(getString(R.string.account_4))
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxSize) {
            val ratio = maxSize.toFloat() / largest.toFloat()
            bitmap.scale(
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1)
            )
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { getString(R.string.account_5) }
            out.toByteArray().also {
                require(it.size <= 5 * 1024 * 1024) { getString(R.string.merchant_onboarding_92) }
            }
        }
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_products_1)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_products_91), 16f, true))
            addView(MerchantUi.muted(context, message).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10))
            })
            addView(MerchantUi.secondaryButton(context, getString(R.string.notifications_5)) { load() })
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
