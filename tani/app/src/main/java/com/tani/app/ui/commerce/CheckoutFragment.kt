package com.tani.app.ui.commerce

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Address
import com.tani.app.data.Cart
import com.tani.app.data.MerchantDeliveryZone
import com.tani.app.data.Repository
import com.tani.app.data.commerce.CartQuote
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class CheckoutFragment : Fragment(R.layout.fragment_checkout) {
    private val repository = Repository()
    private lateinit var addressGroup: RadioGroup
    private lateinit var deliveryZonesBox: LinearLayout
    private lateinit var deliveryHint: TextView
    private lateinit var phone: EditText
    private lateinit var notes: EditText
    private lateinit var progress: ProgressBar
    private lateinit var confirm: Button
    private lateinit var summary: LinearLayout
    private lateinit var total: TextView

    private var addresses: List<Address> = emptyList()
    private var zonesBySeller: Map<String, List<MerchantDeliveryZone>> = emptyMap()
    private val selectedZones = linkedMapOf<String, MerchantDeliveryZone>()
    private var selectedAddressId: String? = null
    private var checkoutKey: String = UUID.randomUUID().toString()
    private var dataLoaded = false
    private var latestQuote: CartQuote? = null
    private var latestQuoteSignature: String? = null
    private var quoteJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkoutKey = savedInstanceState?.getString(STATE_KEY) ?: UUID.randomUUID().toString()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_KEY, checkoutKey)
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (Cart.all().isEmpty()) {
            Toast.makeText(requireContext(), "السلة فارغة", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        addressGroup = view.findViewById(R.id.checkout_addresses)
        deliveryZonesBox = view.findViewById(R.id.checkout_delivery_zones)
        deliveryHint = view.findViewById(R.id.checkout_delivery_hint)
        phone = view.findViewById(R.id.checkout_phone)
        notes = view.findViewById(R.id.checkout_notes)
        progress = view.findViewById(R.id.checkout_progress)
        confirm = view.findViewById(R.id.checkout_confirm)
        summary = view.findViewById(R.id.checkout_summary)
        total = view.findViewById(R.id.checkout_total)

        view.findViewById<Button>(R.id.checkout_add_address).setOnClickListener { showAddAddress() }
        confirm.setOnClickListener { submit() }
        confirm.isEnabled = false
        renderSummary()
        loadCheckoutData()
    }

    override fun onDestroyView() {
        quoteJob?.cancel()
        quoteJob = null
        super.onDestroyView()
    }

    private fun loadCheckoutData() {
        progress.visibility = View.VISIBLE
        dataLoaded = false
        clearQuote()
        updateConfirmState()
        val sellerIds = Cart.groupedBySeller().keys.toList()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                coroutineScope {
                    val addressesDeferred = async { repository.addresses() }
                    val profileDeferred = async { repository.accountProfile() }
                    val zonesDeferred = sellerIds.associateWith { sellerId ->
                        async {
                            repository.merchantDeliveryZones(sellerId)
                                .filter { it.is_active }
                        }
                    }
                    Triple(
                        addressesDeferred.await(),
                        profileDeferred.await(),
                        zonesDeferred.mapValues { (_, deferred) -> deferred.await() }
                    )
                }
            }.onSuccess { (loadedAddresses, account, loadedZones) ->
                addresses = loadedAddresses
                zonesBySeller = loadedZones
                if (phone.text.isBlank()) phone.setText(account.profile.phone)
                renderAddresses()
                renderDeliveryZones()
                dataLoaded = true
                renderSummary()
                updateConfirmState()
                requestQuoteRefresh()
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    it.message ?: "تعذر تحميل بيانات الطلب والتوصيل",
                    Toast.LENGTH_LONG
                ).show()
            }
            progress.visibility = View.GONE
            updateConfirmState()
        }
    }

    private fun renderAddresses() {
        addressGroup.removeAllViews()
        if (addresses.isEmpty()) {
            addressGroup.addView(TextView(requireContext()).apply {
                text = "أضيفي عنواناً قبل تأكيد الطلب."
                setPadding(0, dp(12), 0, dp(12))
            })
            selectedAddressId = null
            return
        }

        val preferred = selectedAddressId
            ?: addresses.firstOrNull { it.is_default }?.id
            ?: addresses.first().id
        selectedAddressId = preferred

        addresses.forEach { address ->
            val radio = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                tag = address.id
                text = buildString {
                    append(address.label)
                    if (address.is_default) append(" • الافتراضي")
                    append("\n${address.description}")
                    address.area?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
                }
                isChecked = address.id == preferred
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        val changed = selectedAddressId != address.id
                        selectedAddressId = address.id
                        address.phone?.takeIf { it.isNotBlank() }?.let { phone.setText(it) }
                        if (changed) {
                            selectedZones.clear()
                            clearQuote()
                            renderDeliveryZones()
                            renderSummary()
                            updateConfirmState()
                            requestQuoteRefresh()
                        }
                    }
                }
            }
            addressGroup.addView(radio)
        }
    }

    private fun renderDeliveryZones() {
        deliveryZonesBox.removeAllViews()
        val grouped = Cart.groupedBySeller()
        var merchantsWithChoices = 0

        grouped.forEach { (sellerId, group) ->
            val first = group.first()
            val storeName = first.product.store_name ?: "المتجر"
            val zones = zonesBySeller[sellerId].orEmpty()

            deliveryZonesBox.addView(TextView(requireContext()).apply {
                text = storeName
                textSize = 16f
                setTextColor(requireContext().getColor(R.color.text_dark))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            })

            if (zones.isEmpty()) {
                deliveryZonesBox.addView(TextView(requireContext()).apply {
                    text = "سيتم حساب التوصيل لهذا المتجر تلقائياً قبل التأكيد"
                    textSize = 14f
                    setTextColor(requireContext().getColor(R.color.text_muted))
                    setPadding(0, 0, 0, dp(10))
                })
                return@forEach
            }

            merchantsWithChoices++
            val preferred = selectedZones[sellerId] ?: suggestedZone(zones)
            if (preferred != null) selectedZones[sellerId] = preferred

            val radioGroup = RadioGroup(requireContext()).apply {
                orientation = RadioGroup.VERTICAL
            }
            zones.forEach { zone ->
                radioGroup.addView(RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    text = buildString {
                        append(zone.area_name)
                        append("\n")
                        append("التوصيل: ${MarketplaceUi.formatPrice(zone.fee)}")
                        zone.estimated_minutes?.let { append(" • ${formatEstimate(it)}") }
                    }
                    isChecked = zone.id == preferred?.id
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            selectedZones[sellerId] = zone
                            clearQuote()
                            renderSummary()
                            updateDeliveryHint()
                            updateConfirmState()
                            requestQuoteRefresh()
                        }
                    }
                })
            }
            deliveryZonesBox.addView(radioGroup)
        }

        if (merchantsWithChoices == 0) {
            deliveryHint.text = multiMerchantPrefix() + "سنحسب رسوم التوصيل تلقائياً قبل التأكيد."
        } else {
            updateDeliveryHint()
        }
    }

    private fun suggestedZone(zones: List<MerchantDeliveryZone>): MerchantDeliveryZone? {
        if (zones.size == 1) return zones.first()
        val addressArea = selectedAddress()?.area?.trim().orEmpty()
        if (addressArea.isBlank()) return null
        return zones.firstOrNull { zone ->
            zone.area_name.equals(addressArea, ignoreCase = true) ||
                zone.area_name.contains(addressArea, ignoreCase = true) ||
                addressArea.contains(zone.area_name, ignoreCase = true)
        }
    }

    private fun updateDeliveryHint() {
        val missing = sellersRequiringZone().count { it !in selectedZones }
        deliveryHint.text = if (missing == 0) {
            multiMerchantPrefix() + "تم اختيار التوصيل لكل متجر ✓"
        } else {
            multiMerchantPrefix() + "اختاري منطقة التوصيل لـ $missing ${if (missing == 1) "متجر" else "متاجر"}."
        }
    }

    private fun multiMerchantPrefix(): String {
        val count = Cart.groupedBySeller().size
        return if (count > 1) "طلبك موزع على $count متاجر • " else ""
    }

    private fun renderSummary() {
        summary.removeAllViews()
        var missingZone = false
        var serverDeliveryNeeded = false
        var selectedDeliveryTotal = 0.0
        val grouped = Cart.groupedBySeller()

        grouped.forEach { (sellerId, group) ->
            val first = group.first()
            val storeName = first.product.store_name ?: "المتجر"
            val subtotal = group.sumOf { it.unitPrice * it.quantity }
            val zones = zonesBySeller[sellerId].orEmpty()
            val chosen = selectedZones[sellerId]
            val delivery = if (zones.isEmpty()) null else chosen?.fee

            if (zones.isEmpty()) serverDeliveryNeeded = true
            if (zones.isNotEmpty() && delivery == null) missingZone = true
            if (delivery != null) selectedDeliveryTotal += delivery

            summary.addView(TextView(requireContext()).apply {
                text = buildString {
                    append(storeName)
                    append("\n${group.sumOf { it.quantity }} منتج • ${MarketplaceUi.formatPrice(subtotal)}")
                    append("\n")
                    when {
                        zones.isEmpty() -> append("التوصيل: يُحسب تلقائياً")
                        delivery == null -> append("التوصيل: اختاري المنطقة")
                        else -> {
                            append("التوصيل: ${MarketplaceUi.formatPrice(delivery)}")
                            chosen?.let { append(" • ${it.area_name}") }
                        }
                    }
                }
                textSize = 15f
                setTextColor(requireContext().getColor(R.color.text_dark))
                setPadding(0, dp(8), 0, dp(8))
            })
        }

        val validQuote = latestQuote?.takeIf { latestQuoteSignature == currentQuoteSignature() }
        total.text = when {
            validQuote != null -> {
                val deliveryTotal = validQuote.deliveries.sumOf { it.fee }
                buildString {
                    append("قيمة المنتجات: ${MarketplaceUi.formatPrice(validQuote.subtotal)}")
                    append("\nالتوصيل: ${MarketplaceUi.formatPrice(deliveryTotal)}")
                    if (validQuote.discount_total > 0) {
                        append("\nالخصم: ${MarketplaceUi.formatPrice(validQuote.discount_total)}")
                    }
                    append("\nالإجمالي النهائي: ${MarketplaceUi.formatPrice(validQuote.grand_total)}")
                }
            }
            missingZone -> "اختاري منطقة التوصيل لإظهار الإجمالي النهائي."
            serverDeliveryNeeded -> "قيمة المنتجات: ${MarketplaceUi.formatPrice(Cart.subtotal())}\nجاري حساب التوصيل والإجمالي النهائي…"
            else -> {
                val estimated = Cart.subtotal() + selectedDeliveryTotal
                "التوصيل: ${MarketplaceUi.formatPrice(selectedDeliveryTotal)}\nالإجمالي المتوقع: ${MarketplaceUi.formatPrice(estimated)}\nيتم التحقق تلقائياً قبل التأكيد."
            }
        }

        confirm.text = if (validQuote != null && !hasBlockingWarnings(validQuote)) {
            "تأكيد الطلب • ${MarketplaceUi.formatPrice(validQuote.grand_total)}"
        } else {
            "مراجعة وتأكيد الطلب"
        }
    }

    private fun showAddAddress() {
        val label = EditText(requireContext()).apply { hint = "اسم العنوان"; setText("المنزل") }
        val description = EditText(requireContext()).apply { hint = "العنوان بالتفصيل" }
        val area = EditText(requireContext()).apply { hint = "المنطقة / الحي" }
        val landmark = EditText(requireContext()).apply { hint = "علامة مميزة" }
        val addressPhone = EditText(requireContext()).apply {
            hint = "هاتف التوصيل"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(phone.text.toString())
        }
        val defaultBox = CheckBox(requireContext()).apply {
            text = "العنوان الافتراضي"
            isChecked = addresses.isEmpty()
        }
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
            addView(label)
            addView(description)
            addView(area)
            addView(landmark)
            addView(addressPhone)
            addView(defaultBox)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("إضافة عنوان")
            .setView(form)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        repository.addAddress(
                            label.text.toString(),
                            description.text.toString(),
                            area.text.toString(),
                            landmark.text.toString(),
                            addressPhone.text.toString(),
                            null,
                            defaultBox.isChecked
                        )
                    }.onSuccess { saved ->
                        selectedAddressId = saved.id
                        selectedZones.clear()
                        clearQuote()
                        phone.setText(saved.phone.orEmpty())
                        dialog.dismiss()
                        loadCheckoutData()
                    }.onFailure {
                        Toast.makeText(
                            requireContext(),
                            it.message ?: "تعذر حفظ العنوان",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun submit() {
        val addressId = selectedAddressId
        if (addressId == null) {
            Toast.makeText(requireContext(), "اختاري عنوان التوصيل", Toast.LENGTH_SHORT).show()
            return
        }
        val phoneValue = phone.text.toString().trim()
        if (phoneValue.length < 7) {
            phone.error = "أدخلي رقم هاتف صحيح للتوصيل"
            phone.requestFocus()
            return
        }
        val missingSeller = sellersRequiringZone().firstOrNull { it !in selectedZones }
        if (missingSeller != null) {
            val store = Cart.groupedBySeller()[missingSeller]?.firstOrNull()?.product?.store_name ?: "المتجر"
            Toast.makeText(requireContext(), "اختاري منطقة التوصيل من $store", Toast.LENGTH_LONG).show()
            return
        }

        val signature = currentQuoteSignature()
        val cachedQuote = latestQuote?.takeIf { latestQuoteSignature == signature }
        if (cachedQuote != null) {
            showQuoteConfirmation(addressId, cachedQuote)
            return
        }

        confirm.isEnabled = false
        progress.visibility = View.VISIBLE
        total.text = "جاري التحقق من السعر والتوصيل…"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.quoteCart(Cart.all(), selectedZones.toMap())
            }.onSuccess { quote ->
                latestQuote = quote
                latestQuoteSignature = signature
                renderSummary()
                showQuoteConfirmation(addressId, quote)
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    it.message ?: "تعذر التحقق من الأسعار ورسوم التوصيل",
                    Toast.LENGTH_LONG
                ).show()
            }
            progress.visibility = View.GONE
            updateConfirmState()
        }
    }

    private fun requestQuoteRefresh() {
        quoteJob?.cancel()
        if (!canQuote()) {
            renderSummary()
            updateConfirmState()
            return
        }

        val signature = currentQuoteSignature()
        if (latestQuote != null && latestQuoteSignature == signature) {
            renderSummary()
            updateConfirmState()
            return
        }

        total.text = "جاري حساب الإجمالي النهائي…"
        quoteJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(180)
            val result = runCatching { repository.quoteCart(Cart.all(), selectedZones.toMap()) }
            if (!isAdded || signature != currentQuoteSignature()) return@launch

            result.onSuccess { quote ->
                latestQuote = quote
                latestQuoteSignature = signature
                renderSummary()
            }.onFailure {
                latestQuote = null
                latestQuoteSignature = null
                renderSummary()
            }
            updateConfirmState()
        }
    }

    private fun showQuoteConfirmation(
        addressId: String,
        quote: CartQuote
    ) {
        val warnings = quoteWarnings(quote)
        if (warnings.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("راجعي السلة")
                .setMessage(warnings.joinToString("\n"))
                .setPositiveButton("حسناً", null)
                .show()
            return
        }

        latestQuote = quote
        latestQuoteSignature = currentQuoteSignature()
        renderSummary()

        val message = buildString {
            append("قيمة المنتجات: ${MarketplaceUi.formatPrice(quote.subtotal)}\n")
            quote.deliveries.forEach { delivery ->
                append("توصيل ${delivery.store_name}: ${MarketplaceUi.formatPrice(delivery.fee)}")
                delivery.area?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                append("\n")
            }
            if (quote.discount_total > 0) {
                append("الخصم: ${MarketplaceUi.formatPrice(quote.discount_total)}\n")
            }
            append("\nالإجمالي النهائي: ${MarketplaceUi.formatPrice(quote.grand_total)}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("التأكيد الأخير")
            .setMessage(message)
            .setNegativeButton("رجوع", null)
            .setPositiveButton("إرسال الطلب") { _, _ -> placeOrder(addressId, quote) }
            .show()
    }

    private fun placeOrder(addressId: String, quote: CartQuote) {
        confirm.isEnabled = false
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.checkoutWithDeliveryZones(
                    addressId = addressId,
                    phone = phone.text.toString().trim(),
                    notes = notes.text.toString().trim(),
                    items = Cart.all(),
                    idempotencyKey = checkoutKey,
                    deliveryZones = selectedZones.toMap(),
                    expectedGrandTotal = quote.grand_total,
                    quoteToken = quote.quote_token
                )
            }.onSuccess { groupId ->
                Cart.clear()
                (activity as? MainActivity)?.refreshCartBadge()
                (activity as? MainActivity)?.show(OrderConfirmationFragment.newInstance(groupId))
            }.onFailure {
                clearQuote()
                renderSummary()
                requestQuoteRefresh()
                Toast.makeText(
                    requireContext(),
                    it.message ?: "تعذر إنشاء الطلب",
                    Toast.LENGTH_LONG
                ).show()
            }
            progress.visibility = View.GONE
            updateConfirmState()
        }
    }

    private fun quoteWarnings(quote: CartQuote): List<String> {
        val unavailable = quote.items.filterNot { it.available }
        return buildList {
            addAll(quote.warnings)
            unavailable.forEach { add("${it.name}: الكمية المطلوبة غير متاحة") }
        }.distinct()
    }

    private fun hasBlockingWarnings(quote: CartQuote): Boolean = quoteWarnings(quote).isNotEmpty()

    private fun canQuote(): Boolean =
        dataLoaded && selectedAddressId != null && sellersRequiringZone().all { it in selectedZones }

    private fun clearQuote() {
        quoteJob?.cancel()
        latestQuote = null
        latestQuoteSignature = null
    }

    private fun currentQuoteSignature(): String = buildString {
        Cart.all().sortedBy { it.key }.forEach { item ->
            append(item.key)
            append(':')
            append(item.quantity)
            append('|')
        }
        append('#')
        selectedZones.toSortedMap().forEach { (sellerId, zone) ->
            append(sellerId)
            append(':')
            append(zone.id)
            append('|')
        }
    }

    private fun sellersRequiringZone(): Set<String> = zonesBySeller
        .filterValues { it.isNotEmpty() }
        .keys

    private fun selectedAddress(): Address? = addresses.firstOrNull { it.id == selectedAddressId }

    private fun updateConfirmState() {
        val allZonesSelected = sellersRequiringZone().all { it in selectedZones }
        confirm.isEnabled = dataLoaded && selectedAddressId != null && allZonesSelected && progress.visibility != View.VISIBLE
    }

    private fun formatEstimate(minutes: Int): String = when {
        minutes < 60 -> "$minutes دقيقة"
        minutes % 60 == 0 -> "${minutes / 60} ساعة"
        else -> "${minutes / 60}س ${minutes % 60}د"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val STATE_KEY = "checkout_key"
    }
}
