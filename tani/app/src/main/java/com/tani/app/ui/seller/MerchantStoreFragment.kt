package com.tani.app.ui.seller

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MerchantStoreFragment : Fragment() {
    private val repository = Repository()
    private lateinit var root: LinearLayout
    private var pendingAsset: String? = null
    private var logoPath: String? = null
    private var coverPath: String? = null
    private var logoStatus: TextView? = null
    private var coverStatus: TextView? = null

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

    override fun onViewCreated(view: View, state: Bundle?) { load() }

    private fun load() {
        loading("جاري تحميل بيانات المتجر…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.merchantStore() ?: error("لم يتم إنشاء المتجر بعد") }
                .onSuccess(::render)
                .onFailure { showError(it.message ?: "تعذر تحميل المتجر") }
        }
    }

    private fun render(store: MerchantStore) {
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "بيانات المتجر"))
        root.addView(MerchantUi.subtitle(context, "عدّلي المعلومات التي يراها العملاء وحالة ظهور متجرك."))

        logoPath = store.logo_url
        coverPath = store.cover_url

        root.addView(MerchantUi.sectionTitle(context, "هوية المتجر"))
        root.addView(MerchantUi.card(context).apply {
            logoStatus = MerchantUi.text(context, if (store.logo_url.isNullOrBlank()) "الشعار: غير مرفوع" else "الشعار: مرفوع ✓", 14f, true)
            coverStatus = MerchantUi.text(context, if (store.cover_url.isNullOrBlank()) "الغلاف: غير مرفوع" else "الغلاف: مرفوع ✓", 14f, true)
            addView(logoStatus)
            addView(MerchantUi.compactButton(context, if (store.logo_url.isNullOrBlank()) "إضافة شعار" else "تغيير الشعار") {
                pendingAsset = "logo"
                pickImage.launch("image/*")
            }.apply { setPadding(MerchantUi.dp(context, 14), 0, MerchantUi.dp(context, 14), 0) })
            addView(coverStatus?.apply { setPadding(0, MerchantUi.dp(context, 12), 0, 0) })
            addView(MerchantUi.compactButton(context, if (store.cover_url.isNullOrBlank()) "إضافة غلاف" else "تغيير الغلاف") {
                pendingAsset = "cover"
                pickImage.launch("image/*")
            })
        })

        root.addView(MerchantUi.sectionTitle(context, "معلومات المتجر"))
        val name = field("اسم المتجر", store.name)
        val description = field("وصف المتجر", store.description, multiline = true)
        val city = field("المدينة", store.city)
        val area = field("المنطقة / الحي", store.area)
        root.addView(MerchantUi.card(context).apply {
            addLabeled(this, "اسم المتجر", name)
            addLabeled(this, "الوصف", description)
            addLabeled(this, "المدينة", city)
            addLabeled(this, "المنطقة / الحي", area)
        })

        root.addView(MerchantUi.sectionTitle(context, "التواصل"))
        val phone = field("هاتف التواصل", store.contact_phone, phone = true)
        val whatsapp = field("رقم واتساب", store.whatsapp, phone = true)
        val sameWhatsApp = CheckBox(context).apply {
            text = "رقم الواتساب هو نفس رقم الهاتف"
            isChecked = !store.contact_phone.isNullOrBlank() && store.contact_phone == store.whatsapp
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    whatsapp.setText(phone.text.toString())
                    whatsapp.isEnabled = false
                } else whatsapp.isEnabled = true
            }
        }
        if (sameWhatsApp.isChecked) whatsapp.isEnabled = false
        root.addView(MerchantUi.card(context).apply {
            addLabeled(this, "هاتف التواصل", phone)
            addLabeled(this, "واتساب", whatsapp)
            addView(sameWhatsApp)
        })

        root.addView(MerchantUi.sectionTitle(context, "حالة المتجر"))
        val open = CheckBox(context).apply { text = "المتجر مفتوح لاستقبال الطلبات"; isChecked = store.is_open }
        val visible = CheckBox(context).apply { text = "المتجر ظاهر للعملاء"; isChecked = store.is_active }
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(open)
            addView(visible)
            addView(MerchantUi.muted(context, "إغلاق المتجر مؤقتاً يوقف استقبال الطلبات بدون حذف المنتجات.", 12f).apply {
                setPadding(0, MerchantUi.dp(context, 6), 0, 0)
            })
        })

        root.addView(MerchantUi.primaryButton(context, "حفظ التغييرات") {
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
        toast("جاري حفظ المتجر…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.updateMerchantStore(
                    store.id,
                    name.text.toString(),
                    description.text.toString(),
                    city.text.toString(),
                    area.text.toString(),
                    phone.text.toString(),
                    whatsapp.text.toString(),
                    open,
                    visible,
                    logoPath,
                    coverPath
                )
            }.onSuccess {
                toast("تم حفظ بيانات المتجر ✓")
                load()
            }.onFailure { toast(it.message ?: "تعذر حفظ المتجر") }
        }
    }

    private fun uploadAsset(uri: Uri) {
        val kind = pendingAsset ?: return
        toast("جاري رفع الصورة…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { imageBytes(uri, 1600, 85) }
                repository.uploadStoreAsset(bytes, kind)
            }.onSuccess { path ->
                if (kind == "logo") {
                    logoPath = path
                    logoStatus?.text = "الشعار: تم اختيار صورة جديدة ✓"
                } else {
                    coverPath = path
                    coverStatus?.text = "الغلاف: تم اختيار صورة جديدة ✓"
                }
                toast("تم رفع الصورة. اضغطي حفظ التغييرات")
            }.onFailure { toast(it.message ?: "تعذر رفع الصورة") }
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
            ?: error("تعذر قراءة الصورة")
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxSize) {
            val ratio = maxSize.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "تعذر تجهيز الصورة" }
            out.toByteArray().also { require(it.size <= 5 * 1024 * 1024) { "الصورة أكبر من 5 ميجابايت" } }
        }
    }

    private fun loading(message: String) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "بيانات المتجر"))
        root.addView(MerchantUi.card(context, soft = true).apply { addView(MerchantUi.text(context, message, 14f, true)) })
    }
    private fun showError(message: String) {
        val context = requireContext(); root.removeAllViews()
        root.addView(MerchantUi.title(context, "بيانات المتجر"))
        root.addView(MerchantUi.card(context).apply {
            addView(MerchantUi.text(context, "تعذر تحميل المتجر", 16f, true))
            addView(MerchantUi.muted(context, message).apply { setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 10)) })
            addView(MerchantUi.secondaryButton(context, "إعادة المحاولة") { load() })
        })
    }
    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()
}
