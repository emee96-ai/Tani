package com.tani.app.ui.commerce

import androidx.core.content.ContextCompat
import com.tani.app.data.addAddress
import com.tani.app.data.addresses
import com.tani.app.data.deleteAddress
import com.tani.app.data.updateAddress

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

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
import com.tani.app.data.cache.AppContentStore
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

    private fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && AppContentStore.addressesLoaded) {
            addresses = AppContentStore.addresses
            progress.visibility = View.GONE
            render()
            return
        }
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.addresses() }
                .onSuccess {
                    addresses = it
                    AppContentStore.updateAddresses(it)
                    render()
                }
                .onFailure {
                    box.removeAllViews()
                    box.addView(message(CustomerErrorMessages.from(it, getString(R.string.addresses_1))))
                }
            progress.visibility = View.GONE
        }
    }

    private fun render() {
        box.removeAllViews()
        if (addresses.isEmpty()) {
            box.addView(message(getString(R.string.addresses_2)))
            return
        }
        addresses.forEach { address ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
            }
            card.addView(TextView(requireContext()).apply {
                text = buildString {
                    append(address.label)
                    if (address.is_default) append(getString(R.string.addresses_3))
                }
                textSize = 22f
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            card.addView(TextView(requireContext()).apply {
                text = buildString {
                    append(address.description)
                    address.area?.takeIf { it.isNotBlank() }?.let { append(getString(R.string.addresses_4, it)) }
                    address.landmark?.takeIf { it.isNotBlank() }?.let { append(getString(R.string.addresses_5, it)) }
                    address.phone?.takeIf { it.isNotBlank() }?.let { append(getString(R.string.addresses_6, it)) }
                }
                textSize = 16f
                setPadding(0, 8, 0, 8)
            })
            val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(requireContext()).apply {
                text = getString(R.string.addresses_7)
                setOnClickListener { showEditor(address) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(Button(requireContext()).apply {
                text = getString(R.string.cart_13)
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
            hint = getString(R.string.addresses_8)
            setText(existing?.label ?: getString(R.string.addresses_9))
        }
        val description = EditText(requireContext()).apply {
            hint = getString(R.string.addresses_10)
            setText(existing?.description.orEmpty())
        }
        val area = EditText(requireContext()).apply {
            hint = getString(R.string.addresses_11)
            setText(existing?.area.orEmpty())
        }
        val landmark = EditText(requireContext()).apply {
            hint = getString(R.string.addresses_12)
            setText(existing?.landmark.orEmpty())
        }
        val phone = EditText(requireContext()).apply {
            hint = getString(R.string.addresses_13)
            inputType = InputType.TYPE_CLASS_PHONE
            setText(existing?.phone.orEmpty())
        }
        val notes = EditText(requireContext()).apply {
            hint = getString(R.string.addresses_14)
            setText(existing?.delivery_notes.orEmpty())
        }
        val defaultBox = CheckBox(requireContext()).apply {
            text = getString(R.string.addresses_15)
            isChecked = existing?.is_default ?: addresses.isEmpty()
        }
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 4)
            addView(label); addView(description); addView(area); addView(landmark); addView(phone); addView(notes); addView(defaultBox)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) getString(R.string.addresses_16) else getString(R.string.addresses_17))
            .setView(form)
            .setPositiveButton(getString(R.string.addresses_18), null)
            .setNegativeButton(getString(R.string.addresses_19), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable {
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
                        load(forceRefresh = true)
                    }.onFailure {
                        Toast.makeText(requireContext(), CustomerErrorMessages.from(it, getString(R.string.addresses_20)), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(address: Address) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.addresses_21))
            .setMessage(getString(R.string.addresses_22, address.label))
            .setPositiveButton(getString(R.string.cart_13)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable { repository.deleteAddress(address.id) }
                        .onSuccess { load(forceRefresh = true) }
                        .onFailure { Toast.makeText(requireContext(), CustomerErrorMessages.from(it, getString(R.string.addresses_23)), Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton(getString(R.string.addresses_19), null)
            .show()
    }

    private fun message(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 16f
        setPadding(6, 20, 6, 20)
        setTextColor(requireContext().getColor(R.color.text_muted))
    }
}
