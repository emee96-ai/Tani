package com.tani.app.ui.commerce

import android.app.AlertDialog
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
import com.tani.app.data.Repository
import com.tani.app.data.commerce.CartQuote
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class CheckoutFragment : Fragment(R.layout.fragment_checkout) {
    private val repository = Repository()
    private lateinit var addressGroup: RadioGroup
    private lateinit var phone: EditText
    private lateinit var notes: EditText
    private lateinit var progress: ProgressBar
    private lateinit var confirm: Button
    private lateinit var summary: LinearLayout
    private lateinit var total: TextView
    private var addresses: List<Address> = emptyList()
    private var selectedAddressId: String? = null
    private var checkoutKey: String = UUID.randomUUID().toString()

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
        phone = view.findViewById(R.id.checkout_phone)
        notes = view.findViewById(R.id.checkout_notes)
        progress = view.findViewById(R.id.checkout_progress)
        confirm = view.findViewById(R.id.checkout_confirm)
        summary = view.findViewById(R.id.checkout_summary)
        total = view.findViewById(R.id.checkout_total)

        view.findViewById<Button>(R.id.checkout_add_address).setOnClickListener { showAddAddress() }
        confirm.setOnClickListener { submit() }
        renderSummary()
        loadAddresses()
    }

    private fun renderSummary() {
        summary.removeAllViews()
        Cart.groupedBySeller().values.forEach { group ->
            val first = group.first()
            val storeName = first.product.store_name ?: "المتجر"
            val subtotal = group.sumOf { it.product.price * it.quantity }
            val delivery = first.product.delivery_fee
            summary.addView(TextView(requireContext()).apply {
                text = "$storeName\n${group.sumOf { it.quantity }} منتج • ${MarketplaceUi.formatPrice(subtotal)}\nالتوصيل: ${MarketplaceUi.formatPrice(delivery)}"
                textSize = 15f
                setPadding(0, 8, 0, 8)
            })
        }
        total.text = "الإجمالي المتوقع: ${MarketplaceUi.formatPrice(Cart.grandTotal())}"
    }

    private fun loadAddresses() {
        progress.visibility = View.VISIBLE
        confirm.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                coroutineScope {
                    val addressesDeferred = async { repository.addresses() }
                    val profileDeferred = async { repository.accountProfile() }
                    addressesDeferred.await() to profileDeferred.await()
                }
            }.onSuccess { (loaded, account) ->
                addresses = loaded
                if (phone.text.isBlank()) phone.setText(account.profile.phone)
                renderAddresses()
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "تعذر تحميل بيانات الدفع", Toast.LENGTH_LONG).show()
            }
            progress.visibility = View.GONE
            confirm.isEnabled = true
        }
    }

    private fun renderAddresses() {
        addressGroup.removeAllViews()
        if (addresses.isEmpty()) {
            addressGroup.addView(TextView(requireContext()).apply {
                text = "أضيفي عنواناً قبل تأكيد الطلب."
                setPadding(0, 12, 0, 12)
            })
            selectedAddressId = null
            return
        }
        val preferred = selectedAddressId ?: addresses.firstOrNull { it.is_default }?.id ?: addresses.first().id
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
                setPadding(6, 8, 6, 8)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedAddressId = address.id
                        address.phone?.takeIf { it.isNotBlank() }?.let { phone.setText(it) }
                    }
                }
            }
            addressGroup.addView(radio)
        }
    }

    private fun showAddAddress() {
        val label = EditText(requireContext()).apply { hint = "اسم العنوان"; setText("المنزل") }
        val description = EditText(requireContext()).apply { hint = "العنوان بالتفصيل" }
        val area = EditText(requireContext()).apply { hint = "المنطقة" }
        val landmark = EditText(requireContext()).apply { hint = "علامة مميزة" }
        val addressPhone = EditText(requireContext()).apply {
            hint = "هاتف التوصيل"; inputType = InputType.TYPE_CLASS_PHONE; setText(phone.text.toString())
        }
        val defaultBox = CheckBox(requireContext()).apply {
            text = "العنوان الافتراضي"; isChecked = addresses.isEmpty()
        }
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 4)
            addView(label); addView(description); addView(area); addView(landmark); addView(addressPhone); addView(defaultBox)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("إضافة عنوان")
            .setView(form)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                lifecycleScope.launch {
                    runCatching {
                        repository.addAddress(
                            label.text.toString(), description.text.toString(), area.text.toString(),
                            landmark.text.toString(), addressPhone.text.toString(), null, defaultBox.isChecked
                        )
                    }.onSuccess { saved ->
                        selectedAddressId = saved.id
                        phone.setText(saved.phone.orEmpty())
                        dialog.dismiss()
                        loadAddresses()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.message ?: "تعذر حفظ العنوان", Toast.LENGTH_LONG).show()
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

        confirm.isEnabled = false
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { repository.quoteCart(Cart.all()) }
                .onSuccess { quote ->
                    progress.visibility = View.GONE
                    confirm.isEnabled = true
                    showQuoteConfirmation(addressId, quote)
                }
                .onFailure {
                    progress.visibility = View.GONE
                    confirm.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        it.message ?: "تعذر التحقق من الأسعار والمخزون",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun showQuoteConfirmation(addressId: String, quote: CartQuote) {
        val unavailable = quote.items.filterNot { it.available }
        val warnings = buildList {
            addAll(quote.warnings)
            unavailable.forEach { add("${it.name}: الكمية المطلوبة غير متاحة") }
        }.distinct()

        total.text = "الإجمالي من الخادم: ${MarketplaceUi.formatPrice(quote.grand_total)}"

        if (warnings.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("راجعي السلة")
                .setMessage(warnings.joinToString("\n"))
                .setPositiveButton("حسناً", null)
                .show()
            return
        }

        val message = buildString {
            append("قيمة المنتجات: ${MarketplaceUi.formatPrice(quote.subtotal)}\n")
            append("التوصيل: ${MarketplaceUi.formatPrice(quote.delivery_total)}\n")
            if (quote.discount_total > 0) {
                append("الخصم: ${MarketplaceUi.formatPrice(quote.discount_total)}\n")
            }
            append("\nالإجمالي النهائي: ${MarketplaceUi.formatPrice(quote.grand_total)}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("تأكيد الطلب")
            .setMessage(message)
            .setNegativeButton("رجوع", null)
            .setPositiveButton("تأكيد الطلب") { _, _ ->
                placeOrder(addressId)
            }
            .show()
    }

    private fun placeOrder(addressId: String) {
        confirm.isEnabled = false
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching {
                repository.checkout(
                    addressId = addressId,
                    phone = phone.text.toString(),
                    notes = notes.text.toString(),
                    items = Cart.all(),
                    idempotencyKey = checkoutKey
                )
            }.onSuccess { groupId ->
                Cart.clear()
                (activity as? MainActivity)?.show(OrderConfirmationFragment.newInstance(groupId))
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "تعذر إنشاء الطلب", Toast.LENGTH_LONG).show()
            }
            progress.visibility = View.GONE
            confirm.isEnabled = true
        }
    }

    companion object {
        private const val STATE_KEY = "checkout_key"
    }
}
