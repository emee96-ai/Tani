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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.Address
import com.tani.app.data.Repository
import kotlinx.coroutines.launch

class AddressesFragment : Fragment(R.layout.fragment_addresses) {
    private val repository = Repository()
    private lateinit var box: LinearLayout
    private lateinit var progress: ProgressBar
    private var addresses: List<Address> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        box = view.findViewById(R.id.addresses_box)
        progress = view.findViewById(R.id.addresses_progress)
        view.findViewById<Button>(R.id.addresses_add).setOnClickListener {
            showEditor(null)
        }
        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { repository.addresses() }
                .onSuccess {
                    addresses = it
                    render()
                }
                .onFailure {
                    box.removeAllViews()
                    box.addView(message(it.message ?: "تعذر تحميل العناوين"))
                }
            progress.visibility = View.GONE
        }
    }

    private fun render() {
        box.removeAllViews()
        if (addresses.isEmpty()) {
            box.addView(message("لا توجد عناوين محفوظة بعد."))
            return
        }
        addresses.forEach { address ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                background = requireContext().getDrawable(R.drawable.bg_card)
            }
            card.addView(TextView(requireContext()).apply {
                text = buildString {
                    append(address.label)
                    if (address.is_default) append("  •  الافتراضي ✓")
                }
                textSize = 18f
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            card.addView(TextView(requireContext()).apply {
                text = buildString {
                    append(address.description)
                    address.area?.takeIf { it.isNotBlank() }?.let { append("\nالمنطقة: $it") }
                    address.landmark?.takeIf { it.isNotBlank() }?.let { append("\nعلامة مميزة: $it") }
                    address.phone?.takeIf { it.isNotBlank() }?.let { append("\nالهاتف: $it") }
                }
                textSize = 15f
                setPadding(0, 8, 0, 8)
            })
            val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(requireContext()).apply {
                text = "تعديل"
                setOnClickListener { showEditor(address) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(Button(requireContext()).apply {
                text = "حذف"
                setOnClickListener { confirmDelete(address) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(actions)
            box.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 })
        }
    }

    private fun showEditor(existing: Address?) {
        val label = EditText(requireContext()).apply {
            hint = "اسم العنوان: المنزل / العمل"
            setText(existing?.label ?: "المنزل")
        }
        val description = EditText(requireContext()).apply {
            hint = "العنوان بالتفصيل"
            setText(existing?.description.orEmpty())
        }
        val area = EditText(requireContext()).apply {
            hint = "المنطقة"
            setText(existing?.area.orEmpty())
        }
        val landmark = EditText(requireContext()).apply {
            hint = "علامة مميزة قريبة"
            setText(existing?.landmark.orEmpty())
        }
        val phone = EditText(requireContext()).apply {
            hint = "رقم هاتف التوصيل"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(existing?.phone.orEmpty())
        }
        val notes = EditText(requireContext()).apply {
            hint = "ملاحظات التوصيل (اختياري)"
            setText(existing?.delivery_notes.orEmpty())
        }
        val defaultBox = CheckBox(requireContext()).apply {
            text = "اجعليه العنوان الافتراضي"
            isChecked = existing?.is_default ?: addresses.isEmpty()
        }
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 4)
            addView(label); addView(description); addView(area); addView(landmark); addView(phone); addView(notes); addView(defaultBox)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "إضافة عنوان" else "تعديل العنوان")
            .setView(form)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                lifecycleScope.launch {
                    runCatching {
                        if (existing == null) {
                            repository.addAddress(
                                label.text.toString(), description.text.toString(), area.text.toString(),
                                landmark.text.toString(), phone.text.toString(), notes.text.toString(), defaultBox.isChecked
                            )
                        } else {
                            repository.updateAddress(
                                existing.id, label.text.toString(), description.text.toString(), area.text.toString(),
                                landmark.text.toString(), phone.text.toString(), notes.text.toString(), defaultBox.isChecked
                            )
                        }
                    }.onSuccess {
                        dialog.dismiss()
                        load()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.message ?: "تعذر حفظ العنوان", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(address: Address) {
        AlertDialog.Builder(requireContext())
            .setTitle("حذف العنوان")
            .setMessage("هل تريدين حذف ${address.label}؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repository.deleteAddress(address.id) }
                        .onSuccess { load() }
                        .onFailure { Toast.makeText(requireContext(), it.message ?: "تعذر الحذف", Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun message(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 16f
        setPadding(6, 20, 6, 20)
        setTextColor(requireContext().getColor(R.color.text_muted))
    }
}
