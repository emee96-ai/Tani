package com.tani.app.ui.seller

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MerchantDeliveryFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private val zoneEditors = mutableListOf<ZoneEditor>()

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
        root.addView(MerchantUi.title(context, getString(R.string.order_details_4)))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_delivery_1), 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val seller = repository.seller() ?: error(getString(R.string.merchant_delivery_2))
                coroutineScope {
                    val zones = async { repository.merchantDeliveryZones(seller.id) }
                    val settings = async { repository.merchantDeliverySettings(seller.id) }
                    Pair(zones.await(), settings.await())
                }
            }.onSuccess { (zones, settings) ->
                val values = if (zones.isNotEmpty()) {
                    zones.map {
                        MerchantDeliveryZoneUpdateInput(
                            area = it.area_name,
                            fee = it.fee,
                            estimatedMinutes = it.estimated_minutes,
                            isActive = it.is_active
                        )
                    }
                } else {
                    listOf(
                        MerchantDeliveryZoneUpdateInput(
                            area = settings?.delivery_area.orEmpty(),
                            fee = settings?.base_fee ?: 0.0,
                            estimatedMinutes = settings?.estimated_minutes,
                            isActive = true
                        )
                    )
                }
                render(values, settings?.notes.orEmpty(), settings?.is_active ?: true)
            }.onFailure { showError(CustomerErrorMessages.from(it, getString(R.string.merchant_delivery_3))) }
        }
    }

    private fun render(initialZones: List<MerchantDeliveryZoneUpdateInput>, initialNotes: String, initialActive: Boolean) {
        val context = requireContext()
        root.removeAllViews()
        zoneEditors.clear()
        root.addView(MerchantUi.title(context, getString(R.string.order_details_4)))
        root.addView(MerchantUi.subtitle(context, getString(R.string.merchant_delivery_4)))

        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_delivery_5), 14.5f, true))
            addView(MerchantUi.muted(context, getString(R.string.merchant_delivery_6), 12.5f).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, 0)
            })
        })

        val zonesBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_delivery_7)))
        root.addView(zonesBox)

        fun appendZone(value: MerchantDeliveryZoneUpdateInput? = null) {
            if (zoneEditors.size >= 20) return toast(getString(R.string.merchant_delivery_8))
            val editor = createZoneEditor(value, zonesBox)
            zoneEditors += editor
            zonesBox.addView(editor.card)
        }

        initialZones.filter { it.area.isNotBlank() || it.fee > 0 }.forEach(::appendZone)
        if (zoneEditors.isEmpty()) appendZone()
        root.addView(MerchantUi.secondaryButton(context, getString(R.string.merchant_delivery_9)) { appendZone() })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_delivery_10)))
        val notes = field(getString(R.string.merchant_delivery_11), initialNotes, multiline = true)
        val active = CheckBox(context).apply {
            text = getString(R.string.merchant_delivery_12)
            isChecked = initialActive
        }
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_delivery_13), 14.5f, true))
            addView(active.apply { setPadding(0, MerchantUi.dp(context, 6), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.muted(context, getString(R.string.merchant_delivery_14), 12f))
            addView(MerchantUi.muted(context, getString(R.string.merchant_delivery_15), 12f).apply { setPadding(0, MerchantUi.dp(context, 12), 0, MerchantUi.dp(context, 5)) })
            addView(notes)
        })

        root.addView(MerchantUi.primaryButton(context, getString(R.string.merchant_delivery_16)) {
            val values = mutableListOf<MerchantDeliveryZoneUpdateInput>()
            zoneEditors.forEach { editor ->
                val area = editor.area.text.toString().trim()
                val fee = editor.fee.text.toString().toDoubleOrNull()
                val minutesText = editor.minutes.text.toString().trim()
                val minutes = minutesText.takeIf { it.isNotEmpty() }?.toIntOrNull()
                when {
                    area.isBlank() -> return@primaryButton toast(getString(R.string.merchant_delivery_17))
                    fee == null || fee < 0 -> return@primaryButton toast(getString(R.string.merchant_delivery_18))
                    minutesText.isNotBlank() && minutes == null -> return@primaryButton toast(getString(R.string.merchant_delivery_19))
                    minutes != null && minutes !in 1..1440 -> return@primaryButton toast(getString(R.string.merchant_delivery_20))
                }
                values += MerchantDeliveryZoneUpdateInput(area, fee, minutes, editor.available.isChecked)
            }
            if (values.map { it.area.lowercase() }.distinct().size != values.size) {
                return@primaryButton toast(getString(R.string.merchant_delivery_21))
            }
            if (active.isChecked && values.none { it.isActive }) {
                return@primaryButton toast(getString(R.string.merchant_delivery_22))
            }
            save(values, notes.text.toString(), active.isChecked)
        })
    }

    private fun createZoneEditor(value: MerchantDeliveryZoneUpdateInput?, parent: LinearLayout): ZoneEditor {
        val context = requireContext()
        val card = MerchantUi.card(context)
        val area = field(getString(R.string.merchant_delivery_23), value?.area)
        val fee = field(getString(R.string.merchant_delivery_24), value?.fee?.takeIf { value.area.isNotBlank() || it > 0 }?.let(::numberText), decimal = true)
        val minutes = field(getString(R.string.merchant_delivery_25), value?.estimatedMinutes?.toString(), number = true)
        val available = CheckBox(context).apply {
            text = getString(R.string.merchant_delivery_26)
            isChecked = value?.isActive ?: true
        }
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.text(context, getString(R.string.merchant_delivery_27), 15.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(MerchantUi.compactButton(context, getString(R.string.cart_13)) {
                if (zoneEditors.size <= 1) return@compactButton toast(getString(R.string.merchant_delivery_28))
                val target = zoneEditors.firstOrNull { it.card === card } ?: return@compactButton
                zoneEditors.remove(target)
                parent.removeView(card)
            })
        })
        card.addView(available.apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
        addLabeled(card, getString(R.string.addresses_11), area)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val feeBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(MerchantUi.muted(context, getString(R.string.merchant_delivery_29), 12f))
            addView(fee)
        }
        val timeBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(MerchantUi.muted(context, getString(R.string.merchant_delivery_30), 12f))
            addView(minutes)
        }
        row.addView(feeBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
        row.addView(timeBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        card.addView(row)
        return ZoneEditor(card, area, fee, minutes, available)
    }

    private fun save(zones: List<MerchantDeliveryZoneUpdateInput>, notes: String, active: Boolean) {
        toast(getString(R.string.merchant_delivery_31))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                repository.saveMerchantDeliveryConfiguration(zones, notes, active)
            }.onSuccess {
                toast(if (active) getString(R.string.merchant_delivery_32) else getString(R.string.merchant_delivery_33))
                load()
            }.onFailure { error ->
                val message = when {
                    error.message.orEmpty().contains("Enable at least one", ignoreCase = true) -> getString(R.string.merchant_delivery_34)
                    error.message.orEmpty().contains("Approved merchant", ignoreCase = true) -> getString(R.string.merchant_delivery_35)
                    else -> CustomerErrorMessages.from(error, getString(R.string.merchant_delivery_36))
                }
                toast(message)
            }
        }
    }

    private fun field(hint: String, value: String?, multiline: Boolean = false, number: Boolean = false, decimal: Boolean = false) = EditText(requireContext()).apply {
        setText(value.orEmpty())
        this.hint = hint
        setBackgroundResource(R.drawable.bg_field)
        setPadding(MerchantUi.dp(context, 12), MerchantUi.dp(context, 11), MerchantUi.dp(context, 12), MerchantUi.dp(context, 11))
        inputType = when {
            decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            number -> InputType.TYPE_CLASS_NUMBER
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multiline) minLines = 3
    }

    private fun addLabeled(parent: LinearLayout, label: String, input: EditText) {
        parent.addView(MerchantUi.muted(requireContext(), label, 12f).apply {
            setPadding(0, MerchantUi.dp(requireContext(), 8), 0, MerchantUi.dp(requireContext(), 5))
        })
        parent.addView(input)
    }

    private fun numberText(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.order_details_4)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_delivery_37), 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, getString(R.string.notifications_5)) { load() })
        })
    }

    private data class ZoneEditor(
        val card: LinearLayout,
        val area: EditText,
        val fee: EditText,
        val minutes: EditText,
        val available: CheckBox
    )
}
