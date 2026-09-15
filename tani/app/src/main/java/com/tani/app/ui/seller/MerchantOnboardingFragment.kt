package com.tani.app.ui.seller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Category
import com.tani.app.data.MerchantProfile
import com.tani.app.data.Repository
import com.tani.app.ui.legal.PoliciesFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MerchantOnboardingFragment : Fragment(R.layout.fragment_merchant_onboarding) {

    private val repository = Repository()
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private lateinit var stepText: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    private var step = 1
    private var merchant: MerchantProfile? = null
    private var categories: List<Category> = emptyList()
    private var identityPath: String? = null
    private val inputs = mutableMapOf<String, EditText>()
    private var categoryField: MaterialAutoCompleteTextView? = null
    private var documentField: MaterialAutoCompleteTextView? = null
    private var policiesCheck: MaterialCheckBox? = null
    private var identityStatus: TextView? = null

    private val prefs by lazy {
        requireContext().getSharedPreferences("merchant_onboarding_draft", android.content.Context.MODE_PRIVATE)
    }

    private val draft = MerchantDraft()

    private val pickIdentity = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadIdentity(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        content = view.findViewById(R.id.merchant_onboarding_content)
        title = view.findViewById(R.id.merchant_onboarding_title)
        stepText = view.findViewById(R.id.merchant_onboarding_step)
        progress = view.findViewById(R.id.merchant_onboarding_progress)
        backButton = view.findViewById(R.id.merchant_onboarding_back)
        nextButton = view.findViewById(R.id.merchant_onboarding_next)

        backButton.setOnClickListener {
            captureCurrentStep()
            if (step > 1) {
                step--
                saveDraft()
                renderStep()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        nextButton.setOnClickListener { advance() }
        loadEntry()
    }

    private fun loadEntry() {
        setBusy(true)
        content.removeAllViews()
        addInfoText("جاري تجهيز تسجيل التاجر…")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                merchant = repository.merchantProfile()
                categories = repository.categories()
                identityPath = repository.merchantIdentityDocuments().firstOrNull()?.storage_path
            }.onSuccess {
                val profile = merchant
                when (profile?.verification_status) {
                    "pending" -> renderStatus(profile, "طلبك قيد المراجعة")
                    "suspended" -> renderStatus(profile, "حساب التاجر موقوف مؤقتاً")
                    "approved" -> renderStatus(profile, "متجرك معتمد ومفعّل", approved = true)
                    else -> {
                        restoreDraft(profile)
                        setBusy(false)
                        renderStep()
                    }
                }
            }.onFailure {
                setBusy(false)
                content.removeAllViews()
                addSectionTitle("تعذر فتح تسجيل التاجر")
                addInfoText(it.message ?: "حدث خطأ غير متوقع")
                addPrimaryButton("إعادة المحاولة") { loadEntry() }
            }
        }
    }

    private fun restoreDraft(profile: MerchantProfile?) {
        val hasDraft = prefs.getBoolean("has_draft", false)
        if (hasDraft) {
            draft.businessName = prefs.getString("business_name", "").orEmpty()
            draft.description = prefs.getString("description", "").orEmpty()
            draft.phone = prefs.getString("phone", "").orEmpty()
            draft.whatsapp = prefs.getString("whatsapp", "").orEmpty()
            draft.categoryId = prefs.getString("category_id", "").orEmpty()
            draft.storeName = prefs.getString("store_name", "").orEmpty()
            draft.storeDescription = prefs.getString("store_description", "").orEmpty()
            draft.city = prefs.getString("city", "كوستي").orEmpty()
            draft.area = prefs.getString("area", "").orEmpty()
            draft.deliveryArea = prefs.getString("delivery_area", "").orEmpty()
            draft.deliveryFee = prefs.getString("delivery_fee", "").orEmpty()
            draft.estimatedMinutes = prefs.getString("estimated_minutes", "").orEmpty()
            draft.documentType = prefs.getString("document_type", "national_id").orEmpty()
            draft.policiesAccepted = prefs.getBoolean("policies", false)
            step = prefs.getInt("step", 1).coerceIn(1, 4)
            return
        }

        draft.businessName = profile?.business_name.orEmpty()
        draft.description = profile?.description.orEmpty()
        draft.phone = profile?.phone.orEmpty()
        draft.whatsapp = profile?.whatsapp.orEmpty()
        draft.categoryId = profile?.category_id.orEmpty()
        draft.storeName = profile?.store_name ?: profile?.business_name.orEmpty()
        draft.storeDescription = profile?.store_description ?: profile?.description.orEmpty()
        draft.city = profile?.city?.takeIf { it.isNotBlank() } ?: "كوستي"
        draft.area = profile?.area.orEmpty()
        draft.deliveryArea = profile?.delivery_area.orEmpty()
        draft.deliveryFee = profile?.delivery_fee?.toString().orEmpty()
        draft.estimatedMinutes = profile?.estimated_minutes?.toString().orEmpty()
        draft.policiesAccepted = profile?.policies_accepted_at != null
    }

    private fun saveDraft() {
        prefs.edit()
            .putBoolean("has_draft", true)
            .putString("business_name", draft.businessName)
            .putString("description", draft.description)
            .putString("phone", draft.phone)
            .putString("whatsapp", draft.whatsapp)
            .putString("category_id", draft.categoryId)
            .putString("store_name", draft.storeName)
            .putString("store_description", draft.storeDescription)
            .putString("city", draft.city)
            .putString("area", draft.area)
            .putString("delivery_area", draft.deliveryArea)
            .putString("delivery_fee", draft.deliveryFee)
            .putString("estimated_minutes", draft.estimatedMinutes)
            .putString("document_type", draft.documentType)
            .putBoolean("policies", draft.policiesAccepted)
            .putInt("step", step)
            .apply()
    }

    private fun renderStep() {
        setBusy(false)
        content.removeAllViews()
        inputs.clear()
        categoryField = null
        documentField = null
        policiesCheck = null
        identityStatus = null

        progress.max = 4
        progress.setProgressCompat(step, true)
        stepText.text = "الخطوة $step من 4"
        backButton.visibility = View.VISIBLE
        nextButton.visibility = View.VISIBLE
        nextButton.text = if (step == 4) "إرسال للمراجعة" else "التالي"

        when (step) {
            1 -> renderBusinessStep()
            2 -> renderStoreStep()
            3 -> renderDeliveryIdentityStep()
            else -> renderReviewStep()
        }
    }

    private fun renderBusinessStep() {
        title.text = "بيانات النشاط"
        addInfoText("خلّي البيانات الأساسية واضحة. رقم الهاتف للتواصل والمراجعة فقط، بدون رمز OTP.")
        addCard {
            addField(this, "business_name", "اسم النشاط", draft.businessName)
            addField(this, "description", "وصف النشاط", draft.description, multiline = true, optional = true)
            addField(this, "phone", "رقم الهاتف", draft.phone, phone = true)
            addField(this, "whatsapp", "واتساب (اختياري)", draft.whatsapp, phone = true, optional = true)
        }
    }

    private fun renderStoreStep() {
        title.text = "بيانات المتجر"
        addInfoText("دي البيانات البتظهر للعميل بعد اعتماد الحساب.")
        addCard {
            addField(this, "store_name", "اسم المتجر", draft.storeName)
            addDropdown(
                this,
                label = "فئة المتجر",
                labels = categories.map { it.name },
                selected = categories.firstOrNull { it.id == draft.categoryId }?.name.orEmpty()
            ) { index -> draft.categoryId = categories.getOrNull(index)?.id.orEmpty() }
            addField(this, "store_description", "وصف المتجر", draft.storeDescription, multiline = true, optional = true)
            addField(this, "city", "المدينة", draft.city)
            addField(this, "area", "المنطقة / الحي (اختياري)", draft.area, optional = true)
        }
    }

    private fun renderDeliveryIdentityStep() {
        title.text = "التوصيل والهوية"
        addInfoText("التاجر يحدد مناطق ورسوم التوصيل. الهوية تُراجع يدويًا بواسطة إدارة تاني.")
        addCard {
            addField(this, "delivery_area", "مناطق التوصيل", draft.deliveryArea, multiline = true)
            addField(this, "delivery_fee", "رسوم التوصيل بالجنيه", draft.deliveryFee, decimal = true)
            addField(this, "estimated_minutes", "زمن التوصيل التقريبي بالدقائق (اختياري)", draft.estimatedMinutes, number = true, optional = true)
        }
        addIdentityCard()
    }

    private fun renderReviewStep() {
        title.text = "راجعي البيانات"
        addInfoText("تأكدي من المعلومات قبل إرسال الطلب للإدارة.")

        addSummaryCard("النشاط", listOf(
            "اسم النشاط" to draft.businessName,
            "الهاتف" to draft.phone,
            "واتساب" to draft.whatsapp.ifBlank { "—" }
        ))
        addSummaryCard("المتجر", listOf(
            "اسم المتجر" to draft.storeName,
            "الفئة" to (categories.firstOrNull { it.id == draft.categoryId }?.name ?: "—"),
            "الموقع" to listOf(draft.city, draft.area).filter { it.isNotBlank() }.joinToString(" - ")
        ))
        addSummaryCard("التوصيل والتحقق", listOf(
            "مناطق التوصيل" to draft.deliveryArea,
            "رسوم التوصيل" to "${draft.deliveryFee} جنيه",
            "الهوية" to if (identityPath.isNullOrBlank()) "غير مرفوعة" else "مرفوعة ✓"
        ))

        addSecondaryButton("عرض اتفاقية وسياسات التاجر") {
            draft.policiesAccepted = policiesCheck?.isChecked ?: draft.policiesAccepted
            saveDraft()
            (activity as? MainActivity)?.show(PoliciesFragment())
        }

        policiesCheck = MaterialCheckBox(requireContext()).apply {
            text = "أوافق على اتفاقية وسياسات التاجر وصحة البيانات والمنتجات والأسعار والتوفر وتنفيذ الطلبات والتوصيل."
            isChecked = draft.policiesAccepted
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
            textSize = 14f
            setPadding(0, dp(10), 0, dp(4))
        }
        content.addView(policiesCheck, matchWrap())
    }

    private fun addIdentityCard() {
        val card = card()
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(sectionText("إثبات الهوية"))
        body.addView(smallText("بطاقة قومية أو جواز سفر. الملف لا يظهر للعملاء."))

        val docLabels = listOf("بطاقة قومية", "جواز سفر", "مستند آخر")
        val docValues = listOf("national_id", "passport", "other")
        addDropdown(
            body,
            "نوع المستند",
            docLabels,
            docLabels.getOrNull(docValues.indexOf(draft.documentType)).orEmpty()
        ) { index -> draft.documentType = docValues.getOrElse(index) { "national_id" } }
        documentField = body.findViewWithTag("dropdown")

        identityStatus = smallText(if (identityPath.isNullOrBlank()) "لم يتم رفع مستند بعد" else "تم رفع مستند الهوية ✓").apply {
            setTextColor(ContextCompat.getColor(requireContext(), if (identityPath.isNullOrBlank()) R.color.text_muted else R.color.success))
            setPadding(0, dp(8), 0, dp(8))
        }
        body.addView(identityStatus)
        val upload = MaterialButton(requireContext()).apply {
            text = if (identityPath.isNullOrBlank()) "رفع الهوية / جواز السفر" else "تغيير المستند"
            isAllCaps = false
            setOnClickListener {
                captureCurrentStep()
                saveDraft()
                pickIdentity.launch("*/*")
            }
        }
        body.addView(upload, matchWrap())
        card.addView(body)
        content.addView(card, matchWrapWithMargins())
    }

    private fun advance() {
        captureCurrentStep()
        if (!validateStep()) return
        saveDraft()
        if (step < 4) {
            step++
            saveDraft()
            renderStep()
        } else {
            submit()
        }
    }

    private fun captureCurrentStep() {
        when (step) {
            1 -> {
                draft.businessName = value("business_name")
                draft.description = value("description")
                draft.phone = value("phone")
                draft.whatsapp = value("whatsapp")
            }
            2 -> {
                draft.storeName = value("store_name")
                draft.storeDescription = value("store_description")
                draft.city = value("city")
                draft.area = value("area")
            }
            3 -> {
                draft.deliveryArea = value("delivery_area")
                draft.deliveryFee = value("delivery_fee")
                draft.estimatedMinutes = value("estimated_minutes")
            }
            4 -> draft.policiesAccepted = policiesCheck?.isChecked ?: draft.policiesAccepted
        }
    }

    private fun validateStep(): Boolean {
        fun fail(message: String): Boolean {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            return false
        }
        return when (step) {
            1 -> {
                val cleanPhone = draft.phone.replace(Regex("[\\s()-]"), "")
                when {
                    draft.businessName.trim().length < 2 -> fail("أدخلي اسم النشاط")
                    !Regex("^\\+?[0-9]{7,15}$").matches(cleanPhone) -> fail("راجعي رقم الهاتف")
                    else -> true
                }
            }
            2 -> when {
                draft.storeName.trim().length < 2 -> fail("أدخلي اسم المتجر")
                draft.categoryId.isBlank() -> fail("اختاري فئة المتجر")
                draft.city.trim().length < 2 -> fail("أدخلي المدينة")
                else -> true
            }
            3 -> {
                val fee = draft.deliveryFee.toDoubleOrNull()
                val minutes = draft.estimatedMinutes.takeIf { it.isNotBlank() }?.toIntOrNull()
                when {
                    draft.deliveryArea.trim().length < 2 -> fail("أدخلي مناطق التوصيل")
                    fee == null || fee < 0 -> fail("راجعي رسوم التوصيل")
                    draft.estimatedMinutes.isNotBlank() && (minutes == null || minutes !in 1..1440) -> fail("زمن التوصيل غير صحيح")
                    identityPath.isNullOrBlank() -> fail("ارفعي مستند الهوية")
                    else -> true
                }
            }
            else -> if (!draft.policiesAccepted) fail("يجب الموافقة على سياسات التاجر") else true
        }
    }

    private fun submit() {
        val identity = identityPath ?: return
        val category = draft.categoryId
        val fee = draft.deliveryFee.toDoubleOrNull() ?: return
        setBusy(true)
        nextButton.text = "جاري الإرسال…"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                repository.submitMerchantApplication(
                    businessName = draft.businessName,
                    description = draft.description,
                    phone = draft.phone,
                    whatsapp = draft.whatsapp,
                    categoryId = category,
                    storeName = draft.storeName,
                    storeDescription = draft.storeDescription,
                    city = draft.city,
                    area = draft.area,
                    deliveryArea = draft.deliveryArea,
                    deliveryFee = fee,
                    estimatedMinutes = draft.estimatedMinutes.toIntOrNull(),
                    identityPath = identity,
                    documentType = draft.documentType,
                    acceptPolicies = draft.policiesAccepted
                )
            }.onSuccess {
                prefs.edit().clear().apply()
                Toast.makeText(requireContext(), "تم إرسال طلب التاجر للمراجعة", Toast.LENGTH_LONG).show()
                loadEntry()
            }.onFailure {
                setBusy(false)
                nextButton.text = "إرسال للمراجعة"
                Toast.makeText(requireContext(), it.message ?: "تعذر إرسال الطلب", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderStatus(profile: MerchantProfile, headline: String, approved: Boolean = false) {
        setBusy(false)
        content.removeAllViews()
        progress.visibility = View.GONE
        stepText.visibility = View.GONE
        backButton.visibility = View.GONE
        nextButton.visibility = View.GONE
        title.text = "حالة حساب التاجر"
        addCard {
            addView(sectionText(headline))
            addView(summaryRow("النشاط", profile.business_name))
            addView(summaryRow("المتجر", profile.store_name ?: "—"))
            addView(summaryRow("الهاتف", profile.phone ?: "—"))
            addView(summaryRow("الحالة", statusText(profile.verification_status)))
            profile.review_note?.takeIf { it.isNotBlank() }?.let { addView(summaryRow("ملاحظة الإدارة", it)) }
        }
        if (approved) {
            addPrimaryButton("فتح لوحة المتجر") { (activity as? MainActivity)?.show(SellerFragment()) }
        } else {
            addSecondaryButton("تحديث الحالة") { loadEntry() }
        }
    }

    private fun uploadIdentity(uri: Uri) {
        setBusy(true)
        identityStatus?.text = "جاري رفع المستند…"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val pair = withContext(Dispatchers.IO) { identityBytes(uri) }
                repository.uploadMerchantIdentity(pair.first, pair.second)
            }.onSuccess { path ->
                identityPath = path
                setBusy(false)
                Toast.makeText(requireContext(), "تم رفع مستند الهوية ✓", Toast.LENGTH_SHORT).show()
                renderStep()
            }.onFailure {
                setBusy(false)
                Toast.makeText(requireContext(), it.message ?: "تعذر رفع مستند الهوية", Toast.LENGTH_LONG).show()
                renderStep()
            }
        }
    }

    private fun identityBytes(uri: Uri): Pair<ByteArray, String> {
        val mime = requireContext().contentResolver.getType(uri).orEmpty()
        if (mime == "application/pdf") {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة الملف")
            require(bytes.size <= 8 * 1024 * 1024) { "الملف أكبر من 8 ميجابايت" }
            return bytes to "application/pdf"
        }
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("تعذر قراءة الصورة")
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > 1600) {
            val ratio = 1600f / largest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)) { "تعذر تجهيز الصورة" }
            out.toByteArray()
        }
        require(bytes.size <= 5 * 1024 * 1024) { "الصورة أكبر من 5 ميجابايت" }
        return bytes to "image/jpeg"
    }

    private fun addField(
        parent: LinearLayout,
        key: String,
        label: String,
        initial: String,
        multiline: Boolean = false,
        number: Boolean = false,
        decimal: Boolean = false,
        phone: Boolean = false,
        optional: Boolean = false
    ) {
        val layout = TextInputLayout(requireContext()).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = ContextCompat.getColor(requireContext(), R.color.border)
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            helperText = if (optional) "اختياري" else null
        }
        val edit = TextInputEditText(layout.context).apply {
            setText(initial)
            textSize = 16f
            inputType = when {
                phone -> InputType.TYPE_CLASS_PHONE
                decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                number -> InputType.TYPE_CLASS_NUMBER
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
            }
        }
        layout.addView(edit, matchWrap())
        parent.addView(layout, matchWrapWithBottomMargin())
        inputs[key] = edit
    }

    private fun addDropdown(
        parent: LinearLayout,
        label: String,
        labels: List<String>,
        selected: String,
        onSelected: (Int) -> Unit
    ) {
        val layout = TextInputLayout(requireContext()).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val field = MaterialAutoCompleteTextView(layout.context).apply {
            setText(selected, false)
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels))
            setOnItemClickListener { _, _, position, _ -> onSelected(position) }
            tag = "dropdown"
        }
        layout.addView(field, matchWrap())
        parent.addView(layout, matchWrapWithBottomMargin())
        if (label == "فئة المتجر") categoryField = field
    }

    private fun addSummaryCard(header: String, rows: List<Pair<String, String>>) {
        val card = card()
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(sectionText(header))
            rows.forEach { (label, value) -> addView(summaryRow(label, value)) }
        }
        card.addView(body)
        content.addView(card, matchWrapWithMargins())
    }

    private fun summaryRow(label: String, value: String): TextView = TextView(requireContext()).apply {
        text = "$label: ${value.ifBlank { "—" }}"
        textSize = 14f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun addCard(build: LinearLayout.() -> Unit) {
        val card = card()
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            build()
        }
        card.addView(body)
        content.addView(card, matchWrapWithMargins())
    }

    private fun card() = MaterialCardView(requireContext()).apply {
        radius = dp(18).toFloat()
        cardElevation = dp(1).toFloat()
        strokeWidth = dp(1)
        strokeColor = ContextCompat.getColor(requireContext(), R.color.border)
        setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.tani_surface))
    }

    private fun sectionText(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
        setPadding(0, 0, 0, dp(8))
    }

    private fun smallText(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 13f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
    }

    private fun addSectionTitle(value: String) {
        content.addView(sectionText(value), matchWrapWithBottomMargin())
    }

    private fun addInfoText(value: String) {
        content.addView(TextView(requireContext()).apply {
            text = value
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            setPadding(0, 0, 0, dp(14))
        }, matchWrap())
    }

    private fun addPrimaryButton(label: String, action: () -> Unit) {
        content.addView(MaterialButton(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.tani_primary)
            setOnClickListener { action() }
        }, matchWrapWithMargins())
    }

    private fun addSecondaryButton(label: String, action: () -> Unit) {
        content.addView(MaterialButton(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(ContextCompat.getColor(requireContext(), R.color.tani_primary))
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.tani_surface)
            strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.tani_primary)
            strokeWidth = dp(1)
            setOnClickListener { action() }
        }, matchWrapWithMargins())
    }

    private fun setBusy(busy: Boolean) {
        backButton.isEnabled = !busy
        nextButton.isEnabled = !busy
    }

    private fun value(key: String): String = inputs[key]?.text?.toString()?.trim().orEmpty()

    private fun statusText(status: String): String = when (status) {
        "pending" -> "قيد المراجعة"
        "approved" -> "معتمد"
        "changes_requested" -> "تعديلات مطلوبة"
        "rejected" -> "مرفوض"
        "suspended" -> "موقوف"
        else -> status
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun matchWrapWithBottomMargin() = matchWrap().apply {
        bottomMargin = dp(12)
    }

    private fun matchWrapWithMargins() = matchWrap().apply {
        topMargin = dp(4)
        bottomMargin = dp(12)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class MerchantDraft(
        var businessName: String = "",
        var description: String = "",
        var phone: String = "",
        var whatsapp: String = "",
        var categoryId: String = "",
        var storeName: String = "",
        var storeDescription: String = "",
        var city: String = "كوستي",
        var area: String = "",
        var deliveryArea: String = "",
        var deliveryFee: String = "",
        var estimatedMinutes: String = "",
        var documentType: String = "national_id",
        var policiesAccepted: Boolean = false
    )
}
