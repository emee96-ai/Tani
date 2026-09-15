package com.tani.app.ui.seller

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
        root.addView(MerchantUi.title(context, "التوصيل"))
        root.addView(MerchantUi.card(context, soft = true).apply { addView(MerchantUi.text(context, "جاري تحميل مناطق التوصيل…", 14f, true)) })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val seller = repository.seller() ?: error("حساب التاجر غير مكتمل")
                coroutineScope {
                    val zones = async { repository.merchantDeliveryZones(seller.id) }
                    val settings = async { repository.merchantDeliverySettings(seller.id) }
                    Triple(seller.id, zones.await(), settings.await())
                }
            }.onSuccess { (sellerId, zones, settings) ->
                val values = if (zones.isNotEmpty()) {
                    zones.map { MerchantDeliveryZoneInput(it.area_name, it.fee, it.estimated_minutes) }
                } else {
                    listOf(MerchantDeliveryZoneInput(settings?.delivery_area.orEmpty(), settings?.base_fee ?: 0.0, settings?.estimated_minutes))
                }
                render(sellerId, values, settings?.notes.orEmpty(), settings?.is_active ?: true)
            }.onFailure { showError(it.message ?: "تعذر تحميل إعدادات التوصيل") }
        }
    }

    private fun render(sellerId: String, initialZones: List<MerchantDeliveryZoneInput>, initialNotes: String, initialActive: Boolean) {
        val context = requireContext()
        root.removeAllViews()
        zoneEditors.clear()
        root.addView(MerchantUi.title(context, "التوصيل"))
        root.addView(MerchantUi.subtitle(context, "حددي سعر وزمن الوصول لكل منطقة. الزمن يبدأ بعد تسليم الطلب لمندوب التوصيل."))

        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, "مهم", 14.5f, true))
            addView(MerchantUi.muted(context, "الرسوم التي تضيفينها هنا هي التي تظهر للعميل عند اختيار منطقته.", 12.5f).apply {
                setPadding(0, MerchantUi.dp(context, 5), 0, 0)
            })
        })

        val zonesBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(MerchantUi.sectionTitle(context, "مناطق ورسوم التوصيل"))
        root.addView(zonesBox)

        fun appendZone(value: MerchantDeliveryZoneInput? = null) {
            if (zoneEditors.size >= 20) return toast("الحد الأقصى 20 منطقة")
            val editor = createZoneEditor(value, zonesBox)
            zoneEditors += editor
            zonesBox.addView(editor.card)
        }

        initialZones.filter { it.area.isNotBlank() || it.fee > 0 }.forEach(::appendZone)
        if (zoneEditors.isEmpty()) appendZone()
        root.addView(MerchantUi.secondaryButton(context, "+ إضافة منطقة أخرى") { appendZone() })

        root.addView(MerchantUi.sectionTitle(context, "إعدادات عامة"))
        val notes = field("مثلاً: التوصيل بعد الساعة 4 عصراً", initialNotes, multiline = true)
        val active = CheckBox(context).apply { text = "التوصيل متاح حالياً"; isChecked = initialActive }
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.muted(context, "ملاحظات التوصيل", 12f).apply { setPadding(0, 0, 0, MerchantUi.dp(context, 5)) })
            addView(notes)
            addView(active.apply { setPadding(0, MerchantUi.dp(context, 10), 0, 0) })
        })

        root.addView(MerchantUi.primaryButton(context, "حفظ إعدادات التوصيل") {
            val values = zoneEditors.mapNotNull { editor ->
                val area = editor.area.text.toString().trim()
                val fee = editor.fee.text.toString().toDoubleOrNull()
                val minutes = editor.minutes.text.toString().trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                when {
                    area.isBlank() -> { toast("اكتبي اسم كل منطقة توصيل"); return@primaryButton }
                    fee == null || fee < 0 -> { toast("راجعي رسوم التوصيل"); return@primaryButton }
                    editor.minutes.text.toString().isNotBlank() && minutes == null -> { toast("راجعي زمن الوصول"); return@primaryButton }
                    minutes != null && minutes !in 1..1440 -> { toast("زمن الوصول يجب أن يكون بين دقيقة و24 ساعة"); return@primaryButton }
                    else -> MerchantDeliveryZoneInput(area, fee, minutes)
                }
            }
            if (values.size != zoneEditors.size) return@primaryButton
            if (values.map { it.area.lowercase() }.distinct().size != values.size) return@primaryButton toast("لا تكرري نفس منطقة التوصيل")
            save(sellerId, values, notes.text.toString(), active.isChecked)
        })
    }

    private fun createZoneEditor(value: MerchantDeliveryZoneInput?, parent: LinearLayout): ZoneEditor {
        val context = requireContext()
        val card = MerchantUi.card(context)
        val area = field("اسم المنطقة / الحي", value?.area)
        val fee = field("الرسوم بالجنيه", value?.fee?.takeIf { value.area.isNotBlank() || it > 0 }?.let(::numberText), decimal = true)
        val minutes = field("زمن الوصول بالدقائق", value?.estimated_minutes?.toString(), number = true)
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(MerchantUi.text(context, "منطقة توصيل", 15.5f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(MerchantUi.compactButton(context, "حذف") {
                if (zoneEditors.size <= 1) return@compactButton toast("يجب أن توجد منطقة توصيل واحدة على الأقل")
                val target = zoneEditors.firstOrNull { it.card === card } ?: return@compactButton
                zoneEditors.remove(target)
                parent.removeView(card)
            })
        })
        addLabeled(card, "المنطقة", area)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val feeBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(MerchantUi.muted(context, "الرسوم", 12f)); addView(fee) }
        val timeBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(MerchantUi.muted(context, "الوصول بعد تسليم الطلب للمندوب", 12f)); addView(minutes) }
        row.addView(feeBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = MerchantUi.dp(context, 4) })
        row.addView(timeBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = MerchantUi.dp(context, 4) })
        card.addView(row)
        return ZoneEditor(card, area, fee, minutes)
    }

    private fun save(sellerId: String, zones: List<MerchantDeliveryZoneInput>, notes: String, active: Boolean) {
        toast("جاري حفظ التوصيل…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.replaceMerchantDeliveryZones(zones)
                val first = zones.first()
                repository.saveMerchantDeliverySettings(sellerId, first.fee, first.area, first.estimated_minutes, notes, active)
            }.onSuccess {
                toast("تم حفظ مناطق التوصيل ✓")
                load()
            }.onFailure { toast(it.message ?: "تعذر حفظ التوصيل") }
        }
    }

    private fun field(hint: String, value: String?, multiline: Boolean = false, number: Boolean = false, decimal: Boolean = false) = EditText(requireContext()).apply {
        setText(value.orEmpty()); this.hint = hint
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
        parent.addView(MerchantUi.muted(requireContext(), label, 12f).apply { setPadding(0, MerchantUi.dp(requireContext(), 8), 0, MerchantUi.dp(requireContext(), 5)) })
        parent.addView(input)
    }
    private fun numberText(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)
    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()
    private fun showError(message: String) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "التوصيل"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل التوصيل", 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }

    private data class ZoneEditor(val card: LinearLayout, val area: EditText, val fee: EditText, val minutes: EditText)
}
