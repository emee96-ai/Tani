package com.tani.app.ui.seller

import com.tani.app.data.merchantStore
import com.tani.app.data.updateMerchantStore
import com.tani.app.data.uploadStoreAsset

import com.tani.app.util.runCatchingCancellable

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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.MerchantStore
import com.tani.app.data.Repository
import com.tani.app.data.network.CustomerErrorMessages
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MerchantStoreFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private var currentStore: MerchantStore? = null
    private var pendingAsset: String? = null
    private var logoPath: String? = null
    private var coverPath: String? = null
    private var logoStatus: TextView? = null
    private var coverStatus: TextView? = null
    private var saveInFlight = false
    private var assetUploadInFlight = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadAsset(uri)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        state: Bundle?
    ): View {
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

    override fun onViewCreated(view: View, state: Bundle?) {
        load()
    }

    private fun load() {
        loading(getString(R.string.merchant_store_1))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.merchantStore() ?: error(getString(R.string.merchant_store_2)) }
                .onSuccess(::render)
                .onFailure { error ->
                    showError(CustomerErrorMessages.from(error, getString(R.string.merchant_store_3)))
                }
        }
    }

    private fun render(store: MerchantStore) {
        val context = requireContext()
        currentStore = store
        saveInFlight = false
        assetUploadInFlight = false
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_onboarding_22)))
        root.addView(MerchantUi.subtitle(context, getString(R.string.merchant_store_4)))

        logoPath = store.logo_url
        coverPath = store.cover_url

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_store_5)))
        root.addView(MerchantUi.card(context).apply {
            logoStatus = MerchantUi.text(context, if (store.logo_url.isNullOrBlank()) getString(R.string.merchant_store_6) else getString(R.string.merchant_store_7), 14f, true)
            coverStatus = MerchantUi.text(context, if (store.cover_url.isNullOrBlank()) getString(R.string.merchant_store_8) else getString(R.string.merchant_store_9), 14f, true)
            addView(logoStatus)
            addView(MerchantUi.compactButton(context, if (store.logo_url.isNullOrBlank()) getString(R.string.merchant_store_10) else getString(R.string.merchant_store_11)) {
                if (assetUploadInFlight) return@compactButton
                pendingAsset = "logo"
                pickImage.launch("image/*")
            }.apply { setPadding(MerchantUi.dp(context, 14), 0, MerchantUi.dp(context, 14), 0) })
            addView(coverStatus?.apply { setPadding(0, MerchantUi.dp(context, 12), 0, 0) })
            addView(MerchantUi.compactButton(context, if (store.cover_url.isNullOrBlank()) getString(R.string.merchant_store_12) else getString(R.string.merchant_store_13)) {
                if (assetUploadInFlight) return@compactButton
                pendingAsset = "cover"
                pickImage.launch("image/*")
            })
            addView(MerchantUi.muted(context, getString(R.string.merchant_store_14), 12f).apply {
                setPadding(0, MerchantUi.dp(context, 8), 0, 0)
            })
        })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_store_15)))
        val name = field(getString(R.string.merchant_onboarding_25), store.name)
        val description = field(getString(R.string.merchant_onboarding_29), store.description, multiline = true)
        val city = field(getString(R.string.merchant_onboarding_30), store.city)
        val area = field(getString(R.string.checkout_25), store.area)
        root.addView(MerchantUi.card(context).apply {
            addLabeled(this, getString(R.string.merchant_onboarding_25), name)
            addLabeled(this, getString(R.string.merchant_products_28), description)
            addLabeled(this, getString(R.string.merchant_onboarding_30), city)
            addLabeled(this, getString(R.string.checkout_25), area)
        })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_store_16)))
        val phone = field(getString(R.string.merchant_store_17), store.contact_phone, phone = true)
        val whatsapp = field(getString(R.string.merchant_store_18), store.whatsapp, phone = true)
        val sameWhatsApp = CheckBox(context).apply {
            text = getString(R.string.merchant_onboarding_21)
            isChecked = !store.contact_phone.isNullOrBlank() && store.contact_phone == store.whatsapp
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    whatsapp.setText(phone.text.toString())
                    whatsapp.isEnabled = false
                } else {
                    whatsapp.isEnabled = true
                }
            }
        }
        if (sameWhatsApp.isChecked) whatsapp.isEnabled = false
        root.addView(MerchantUi.card(context).apply {
            addLabeled(this, getString(R.string.merchant_store_17), phone)
            addLabeled(this, getString(R.string.merchant_onboarding_44), whatsapp)
            addView(sameWhatsApp)
        })

        root.addView(MerchantUi.sectionTitle(context, getString(R.string.merchant_store_19)))
        val open = CheckBox(context).apply {
            text = getString(R.string.merchant_store_20)
            isChecked = store.is_open
        }
        val visible = CheckBox(context).apply {
            text = getString(R.string.merchant_store_21)
            isChecked = store.is_active
        }
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(open)
            addView(visible)
            addView(MerchantUi.muted(context, getString(R.string.merchant_store_22), 12f).apply {
                setPadding(0, MerchantUi.dp(context, 6), 0, 0)
            })
        })

        root.addView(MerchantUi.primaryButton(context, getString(R.string.merchant_store_23)) {
            if (saveInFlight) return@primaryButton
            if (sameWhatsApp.isChecked) whatsapp.setText(phone.text.toString())
            save(store, name, description, city, area, phone, whatsapp, open.isChecked, visible.isChecked)
        })
    }

    private fun save(
        store: MerchantStore,
        name: EditText,
        description: EditText,
        city: EditText,
        area: EditText,
        phone: EditText,
        whatsapp: EditText,
        open: Boolean,
        visible: Boolean
    ) {
        if (saveInFlight) return
        val cleanName = name.text.toString().trim()
        val cleanCity = city.text.toString().trim()
        if (cleanName.length < 2) return toast(getString(R.string.merchant_onboarding_66))
        if (cleanCity.length < 2) return toast(getString(R.string.merchant_onboarding_68))

        saveInFlight = true
        toast(getString(R.string.merchant_store_24))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                repository.updateMerchantStore(
                    store.id,
                    cleanName,
                    description.text.toString(),
                    cleanCity,
                    area.text.toString(),
                    phone.text.toString(),
                    whatsapp.text.toString(),
                    open,
                    visible,
                    logoPath,
                    coverPath
                )
            }.onSuccess { updated ->
                currentStore = updated
                toast(getString(R.string.merchant_store_25))
                render(updated)
            }.onFailure { error ->
                saveInFlight = false
                toast(CustomerErrorMessages.from(error, merchantFallback(error, getString(R.string.merchant_store_26))))
            }
        }
    }

    private fun uploadAsset(uri: Uri) {
        if (assetUploadInFlight) return
        val kind = pendingAsset ?: return
        val store = currentStore ?: return toast(getString(R.string.merchant_store_27))
        assetUploadInFlight = true
        if (kind == "logo") logoStatus?.text = getString(R.string.merchant_store_28) else coverStatus?.text = getString(R.string.merchant_store_29)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 85) }
                val path = repository.uploadStoreAsset(bytes, kind)
                val nextLogo = if (kind == "logo") path else store.logo_url
                val nextCover = if (kind == "cover") path else store.cover_url
                repository.updateMerchantStore(
                    store.id,
                    store.name,
                    store.description,
                    store.city,
                    store.area.orEmpty(),
                    store.contact_phone.orEmpty(),
                    store.whatsapp.orEmpty(),
                    store.is_open,
                    store.is_active,
                    nextLogo,
                    nextCover
                )
            }.onSuccess { updated ->
                currentStore = updated
                logoPath = updated.logo_url
                coverPath = updated.cover_url
                assetUploadInFlight = false
                if (kind == "logo") {
                    logoStatus?.text = getString(R.string.merchant_store_7)
                    toast(getString(R.string.merchant_store_30))
                } else {
                    coverStatus?.text = getString(R.string.merchant_store_9)
                    toast(getString(R.string.merchant_store_31))
                }
            }.onFailure { error ->
                assetUploadInFlight = false
                if (kind == "logo") {
                    logoStatus?.text = if (logoPath.isNullOrBlank()) getString(R.string.merchant_store_6) else getString(R.string.merchant_store_7)
                } else {
                    coverStatus?.text = if (coverPath.isNullOrBlank()) getString(R.string.merchant_store_8) else getString(R.string.merchant_store_9)
                }
                toast(CustomerErrorMessages.from(error, merchantFallback(error, getString(R.string.merchant_store_32))))
            }
        }
    }

    private fun merchantFallback(error: Throwable, fallback: String): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains(getString(R.string.merchant_onboarding_25), ignoreCase = true) -> getString(R.string.merchant_store_33)
            raw.contains(getString(R.string.merchant_onboarding_30), ignoreCase = true) -> getString(R.string.merchant_store_34)
            raw.contains(getString(R.string.merchant_store_35), ignoreCase = true) -> getString(R.string.merchant_store_36)
            else -> CustomerErrorMessages.from(error, fallback)
        }
    }

    private fun field(hint: String, value: String?, multiline: Boolean = false, phone: Boolean = false) = EditText(requireContext()).apply {
        setText(value.orEmpty())
        this.hint = hint
        setBackgroundResource(R.drawable.bg_field)
        setPadding(MerchantUi.dp(context, 14), MerchantUi.dp(context, 12), MerchantUi.dp(context, 14), MerchantUi.dp(context, 12))
        inputType = when {
            phone -> InputType.TYPE_CLASS_PHONE
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        if (multiline) minLines = 3
    }

    private fun addLabeled(parent: LinearLayout, label: String, input: EditText) {
        val context = requireContext()
        parent.addView(MerchantUi.muted(context, label, 12f).apply { setPadding(0, MerchantUi.dp(context, 8), 0, MerchantUi.dp(context, 5)) })
        parent.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun imageBytes(uri: Uri, maxSize: Int, quality: Int): ByteArray {
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error(getString(R.string.account_4))
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxSize) {
            val ratio = maxSize.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { getString(R.string.account_5) }
            out.toByteArray().also { require(it.size <= 5 * 1024 * 1024) { getString(R.string.merchant_onboarding_92) } }
        }
    }

    private fun loading(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_onboarding_22)))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, message, 14f, true))
        })
    }

    private fun showError(message: String) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, getString(R.string.merchant_onboarding_22)))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, getString(R.string.merchant_store_37), 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, getString(R.string.notifications_5)) { load() })
        })
    }

    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()
}
