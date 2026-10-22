package com.tani.app.ui.commerce

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

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
import com.google.android.material.snackbar.Snackbar
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.*
import com.tani.app.data.commerce.CartQuote
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private var dataLoaded = false
    private var latestQuote: CartQuote? = null
    private var latestQuoteSignature: String? = null
    private var quoteJob: Job? = null

    // اختيارات العنوان والمنطقة قبل تدوير الشاشة أو إيقاف النظام للتطبيق؛ تُطبق بعد تحميل البيانات.
    private var restoredAddressId: String? = null
    private var restoredZoneIds: Map<String, String> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.let { state ->
            restoredAddressId = state.getString(STATE_ADDRESS_ID)
            restoredZoneIds = state.getStringArrayList(STATE_ZONE_IDS).orEmpty()
                .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (sellerId, zoneId) -> sellerId to zoneId }
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
        recoverCheckout()
    }

    private fun recoverCheckout() {
        confirm.isEnabled = false
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                repository.recoverPendingCheckout()
            }.onSuccess { groupId ->
                if (groupId != null) {
                    (activity as? MainActivity)?.show(OrderConfirmationFragment.newInstance(groupId))
                } else if (Cart.all().isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.checkout_1), Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    confirm.setOnClickListener { submit() }
                    loadCheckoutData()
                }
            }.onFailure {
                progress.visibility = View.GONE
                confirm.text = getString(R.string.checkout_2)
                confirm.isEnabled = true
                confirm.setOnClickListener { recoverCheckout() }
                showError(CustomerErrorMessages.from(it, getString(R.string.checkout_3)))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ADDRESS_ID, selectedAddressId ?: restoredAddressId)
        val zoneIds = restoredZoneIds + selectedZones.mapValues { (_, zone) -> zone.id }
        outState.putStringArrayList(
            STATE_ZONE_IDS,
            ArrayList(zoneIds.map { (sellerId, zoneId) -> "$sellerId=$zoneId" })
        )
    }

    private fun applyRestoredSelections() {
        restoredAddressId?.takeIf { id -> addresses.any { it.id == id } }?.let { selectedAddressId = it }
        restoredZoneIds.forEach { (sellerId, zoneId) ->
            zonesBySeller[sellerId]?.firstOrNull { it.id == zoneId }?.let { selectedZones[sellerId] = it }
        }
        restoredAddressId = null
        restoredZoneIds = emptyMap()
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
            runCatchingCancellable {
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
                applyRestoredSelections()
                renderAddresses()
                renderDeliveryZones()
                dataLoaded = true
                renderSummary()
                updateConfirmState()
                requestQuoteRefresh()
            }.onFailure {
                showError(CustomerErrorMessages.from(it, getString(R.string.checkout_4)))
            }
            progress.visibility = View.GONE
            updateConfirmState()
        }
    }

    private fun renderAddresses() {
        addressGroup.removeAllViews()
        if (addresses.isEmpty()) {
            addressGroup.addView(TextView(requireContext()).apply {
                text = getString(R.string.checkout_5)
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
                    if (address.is_default) append(getString(R.string.checkout_6))
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
            val storeName = first.product.store_name ?: getString(R.string.main_18)
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
                    text = getString(R.string.checkout_7)
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
                        append(getString(R.string.checkout_8, (MarketplaceUi.formatPrice(zone.fee))))
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
            deliveryHint.text = withMerchantPrefix(getString(R.string.checkout_9))
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
            withMerchantPrefix(getString(R.string.checkout_10))
        } else {
            withMerchantPrefix(resources.getQuantityString(R.plurals.checkout_missing_zones, missing, missing.toString()))
        }
    }

    private fun withMerchantPrefix(message: String): String {
        val count = Cart.groupedBySeller().size
        return if (count > 1) getString(R.string.checkout_11, count.toString(), message) else message
    }

    private fun renderSummary() {
        summary.removeAllViews()
        var missingZone = false
        var serverDeliveryNeeded = false
        var selectedDeliveryTotal = 0.0
        val grouped = Cart.groupedBySeller()

        grouped.forEach { (sellerId, group) ->
            val first = group.first()
            val storeName = first.product.store_name ?: getString(R.string.main_18)
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
                    append(getString(R.string.checkout_12, (group.sumOf { it.quantity }).toString(), (MarketplaceUi.formatPrice(subtotal))))
                    append("\n")
                    when {
                        zones.isEmpty() -> append(getString(R.string.checkout_13))
                        delivery == null -> append(getString(R.string.checkout_14))
                        else -> {
                            append(getString(R.string.checkout_8, (MarketplaceUi.formatPrice(delivery))))
                            chosen?.let { append(" • ${it.area_name}") }
                        }
                    }
                }
                textSize = 16f
                setTextColor(requireContext().getColor(R.color.text_dark))
                setPadding(0, dp(8), 0, dp(8))
            })
        }

        val validQuote = latestQuote?.takeIf { latestQuoteSignature == currentQuoteSignature() }
        total.text = when {
            validQuote != null -> {
                val deliveryTotal = validQuote.deliveries.sumOf { it.fee }
                buildString {
                    append(getString(R.string.checkout_15, (MarketplaceUi.formatPrice(validQuote.subtotal))))
                    append(getString(R.string.checkout_16, (MarketplaceUi.formatPrice(deliveryTotal))))
                    if (validQuote.discount_total > 0) {
                        append(getString(R.string.checkout_17, (MarketplaceUi.formatPrice(validQuote.discount_total))))
                    }
                    append(getString(R.string.checkout_18, (MarketplaceUi.formatPrice(validQuote.grand_total))))
                }
            }
            missingZone -> getString(R.string.checkout_19)
            serverDeliveryNeeded -> getString(R.string.checkout_20, (MarketplaceUi.formatPrice(Cart.subtotal())))
            else -> {
                val estimated = Cart.subtotal() + selectedDeliveryTotal
                getString(R.string.checkout_21, (MarketplaceUi.formatPrice(selectedDeliveryTotal)), (MarketplaceUi.formatPrice(estimated)))
            }
        }

        confirm.text = if (validQuote != null && !hasBlockingWarnings(validQuote)) {
            getString(R.string.checkout_22, (MarketplaceUi.formatPrice(validQuote.grand_total)))
        } else {
            getString(R.string.checkout_23)
        }
    }

    private fun showAddAddress() {
        val label = EditText(requireContext()).apply { hint = getString(R.string.checkout_24); setText(getString(R.string.addresses_9)) }
        val description = EditText(requireContext()).apply { hint = getString(R.string.addresses_10) }
        val area = EditText(requireContext()).apply { hint = getString(R.string.checkout_25) }
        val landmark = EditText(requireContext()).apply { hint = getString(R.string.checkout_26) }
        val addressPhone = EditText(requireContext()).apply {
            hint = getString(R.string.checkout_27)
            inputType = InputType.TYPE_CLASS_PHONE
            setText(phone.text.toString())
        }
        val defaultBox = CheckBox(requireContext()).apply {
            text = getString(R.string.checkout_28)
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
            .setTitle(getString(R.string.addresses_16))
            .setView(form)
            .setPositiveButton(getString(R.string.addresses_18), null)
            .setNegativeButton(getString(R.string.addresses_19), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable {
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
                            CustomerErrorMessages.from(it, getString(R.string.addresses_20)),
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
            showError(getString(R.string.checkout_29))
            return
        }
        val phoneValue = phone.text.toString().trim()
        if (phoneValue.length < 7) {
            phone.error = getString(R.string.checkout_30)
            phone.requestFocus()
            return
        }
        val missingSeller = sellersRequiringZone().firstOrNull { it !in selectedZones }
        if (missingSeller != null) {
            val store = Cart.groupedBySeller()[missingSeller]?.firstOrNull()?.product?.store_name ?: getString(R.string.main_18)
            showError(getString(R.string.checkout_31, store))
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
        total.text = getString(R.string.checkout_32)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                repository.quoteCart(Cart.all(), selectedZones.toMap())
            }.onSuccess { quote ->
                latestQuote = quote
                latestQuoteSignature = signature
                renderSummary()
                showQuoteConfirmation(addressId, quote)
            }.onFailure {
                showError(CustomerErrorMessages.from(it, getString(R.string.checkout_33)))
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

        total.text = getString(R.string.checkout_34)
        quoteJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(180)
            val result = runCatchingCancellable { repository.quoteCart(Cart.all(), selectedZones.toMap()) }
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
                .setTitle(getString(R.string.checkout_35))
                .setMessage(warnings.joinToString("\n"))
                .setPositiveButton(getString(R.string.checkout_36), null)
                .show()
            return
        }

        latestQuote = quote
        latestQuoteSignature = currentQuoteSignature()
        renderSummary()

        val message = buildString {
            append(getString(R.string.checkout_37, (MarketplaceUi.formatPrice(quote.subtotal))))
            quote.deliveries.forEach { delivery ->
                append(getString(R.string.checkout_38, delivery.store_name, (MarketplaceUi.formatPrice(delivery.fee))))
                delivery.area?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                append("\n")
            }
            if (quote.discount_total > 0) {
                append(getString(R.string.checkout_39, (MarketplaceUi.formatPrice(quote.discount_total))))
            }
            append(getString(R.string.checkout_18, (MarketplaceUi.formatPrice(quote.grand_total))))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.checkout_40))
            .setMessage(message)
            .setNegativeButton(getString(R.string.checkout_41), null)
            .setPositiveButton(getString(R.string.checkout_42)) { _, _ -> placeOrder(addressId, quote) }
            .show()
    }

    private fun placeOrder(addressId: String, quote: CartQuote) {
        confirm.isEnabled = false
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val uid = Supabase.userId ?: error(getString(R.string.checkout_43))
                val attempt = PendingCheckoutStore.begin(uid)
                repository.checkoutWithDeliveryZones(
                    addressId = addressId,
                    phone = phone.text.toString().trim(),
                    notes = notes.text.toString().trim(),
                    items = Cart.all(),
                    idempotencyKey = attempt.key,
                    deliveryZones = selectedZones.toMap(),
                    expectedGrandTotal = quote.grand_total,
                    quoteToken = quote.quote_token
                )
            }.onSuccess { groupId ->
                (activity as? MainActivity)?.show(OrderConfirmationFragment.newInstance(groupId))
            }.onFailure {
                clearQuote()
                renderSummary()
                requestQuoteRefresh()
                showError(CustomerErrorMessages.from(it, getString(R.string.checkout_44)))
            }
            progress.visibility = View.GONE
            updateConfirmState()
        }
    }

    private fun quoteWarnings(quote: CartQuote): List<String> {
        val unavailable = quote.items.filterNot { it.available }
        return buildList {
            addAll(quote.warnings)
            unavailable.forEach { add(getString(R.string.checkout_45, it.name)) }
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
        minutes < 60 -> getString(R.string.checkout_46, minutes.toString())
        minutes % 60 == 0 -> getString(R.string.checkout_47, (minutes / 60).toString())
        else -> getString(R.string.checkout_48, (minutes / 60).toString(), (minutes % 60).toString())
    }

    // Checkout errors stay on screen long enough to read, then dismiss themselves.
    private fun showError(message: String) {
        val root = view ?: return
        Snackbar.make(root, message, ERROR_DURATION_MS)
            .setTextMaxLines(5)
            .apply { activity?.findViewById<View>(R.id.bottom_nav)?.takeIf { it.isShown }?.let { anchorView = it } }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATE_ADDRESS_ID = "checkout_selected_address_id"
        const val STATE_ZONE_IDS = "checkout_selected_zone_ids"
        const val ERROR_DURATION_MS = 8_000
    }
}
