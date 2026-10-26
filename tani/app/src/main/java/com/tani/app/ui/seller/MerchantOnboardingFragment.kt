package com.tani.app.ui.seller

import androidx.core.content.edit
import androidx.core.graphics.scale
import com.tani.app.data.categories
import com.tani.app.data.merchantIdentityDocuments
import com.tani.app.data.merchantProfile
import com.tani.app.data.submitMerchantApplication
import com.tani.app.data.uploadMerchantIdentity

import com.tani.app.util.decodeBoundedBitmap
import com.tani.app.util.runCatchingCancellable

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.tani.app.data.MerchantDeliveryZoneInput
import com.tani.app.data.MerchantProfile
import com.tani.app.data.PrivateUserData
import com.tani.app.data.Repository
import com.tani.app.data.Supabase
import com.tani.app.data.network.CustomerErrorMessages
import com.tani.app.ui.legal.PoliciesFragment
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MerchantOnboardingFragment : Fragment(R.layout.fragment_merchant_onboarding) {

    private val repository = Repository()
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private lateinit var stepText: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var submitProgress: ProgressBar
    private lateinit var submitStatus: TextView

    private var step = 1
    private var merchant: MerchantProfile? = null
    private var categories: List<Category> = emptyList()
    private var identityPath: String? = null
    private var reviewNotice: String? = null
    private var submissionInFlight = false
    // true فقط أثناء عرض خطوة نموذج؛ يمنع حفظ حقول فارغة فوق المسودة أثناء التحميل أو شاشة الحالة.
    private var formVisible = false
    private val inputs = mutableMapOf<String, EditText>()
    private var categoryField: MaterialAutoCompleteTextView? = null
    private var documentField: MaterialAutoCompleteTextView? = null
    private var policiesCheck: MaterialCheckBox? = null
    private var identityStatus: TextView? = null
    private var whatsappSameCheck: MaterialCheckBox? = null

    private val prefs by lazy {
        PrivateUserData.merchantDraft(Supabase.userId ?: error("تسجيل الدخول مطلوب"))
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
        submitProgress = view.findViewById(R.id.merchant_onboarding_submit_progress)
        submitStatus = view.findViewById(R.id.merchant_onboarding_status)

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

    override fun onPause() {
        super.onPause()
        // يحفظ ما كُتب في الخطوة الحالية قبل تدوير الشاشة أو انتقال التطبيق للخلفية.
        if (formVisible && !submissionInFlight) {
            captureCurrentStep()
            saveDraft()
        }
    }

    private fun loadEntry() {
        formVisible = false
        submitStatus.text = getString(R.string.merchant_onboarding_1)
        setBusy(true)
        content.removeAllViews()
        addInfoText(getString(R.string.merchant_onboarding_1))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                coroutineScope {
                    val profileRequest = async { repository.merchantProfile() }
                    val categoriesRequest = async {
                        runCatchingCancellable { repository.categories() }.getOrDefault(emptyList())
                    }
                    val documentsRequest = async {
                        runCatchingCancellable { repository.merchantIdentityDocuments() }.getOrDefault(emptyList())
                    }
                    Triple(
                        profileRequest.await(),
                        categoriesRequest.await(),
                        documentsRequest.await()
                    )
                }
            }.onSuccess { (profile, loadedCategories, documents) ->
                merchant = profile
                categories = loadedCategories
                identityPath = documents.firstOrNull()?.storage_path
                    ?: prefs.getString("identity_path", null)
                reviewNotice = null
                when (profile?.verification_status) {
                    "pending" -> renderStatus(profile, getString(R.string.merchant_onboarding_2))
                    "suspended" -> renderStatus(profile, getString(R.string.merchant_onboarding_3))
                    "approved" -> renderStatus(profile, getString(R.string.merchant_onboarding_4), approved = true)
                    "changes_requested" -> openForReviewChanges(
                        profile,
                        getString(R.string.merchant_onboarding_5)
                    )
                    "rejected" -> openForReviewChanges(
                        profile,
                        getString(R.string.merchant_onboarding_6)
                    )
                    else -> {
                        restoreDraft(profile)
                        setBusy(false)
                        renderStep()
                    }
                }
            }.onFailure { error ->
                setBusy(false)
                content.removeAllViews()
                addSectionTitle(getString(R.string.merchant_onboarding_7))
                addInfoText(merchantError(error, getString(R.string.merchant_onboarding_8)))
                addPrimaryButton(getString(R.string.notifications_5)) { loadEntry() }
            }
        }
    }

    private fun openForReviewChanges(profile: MerchantProfile, headline: String) {
        restoreDraft(profile)
        step = prefs.getInt("step", 1).coerceIn(1, 4)
        reviewNotice = buildString {
            append(headline)
            profile.review_note?.trim()?.takeIf { it.isNotBlank() }?.let {
                append(getString(R.string.merchant_onboarding_9))
                append(it)
            }
        }
        setBusy(false)
        renderStep()
    }

    private fun restoreDraft(profile: MerchantProfile?) {
        val hasDraft = prefs.getBoolean("has_draft", false)
        if (hasDraft) {
            draft.businessName = prefs.getString("business_name", "").orEmpty()
            draft.description = prefs.getString("description", "").orEmpty()
            draft.phone = prefs.getString("phone", "").orEmpty()
            draft.whatsapp = prefs.getString("whatsapp", "").orEmpty()
            draft.whatsappSameAsPhone = prefs.getBoolean("whatsapp_same", false)
            draft.categoryId = prefs.getString("category_id", "").orEmpty()
            draft.requestedCategory = prefs.getString("requested_category", "").orEmpty()
            draft.storeName = prefs.getString("store_name", "").orEmpty()
            draft.storeDescription = prefs.getString("store_description", "").orEmpty()
            draft.city = prefs.getString("city", getString(R.string.merchant_onboarding_10)).orEmpty()
            draft.area = prefs.getString("area", "").orEmpty()
            draft.deliveryZones = restoreDeliveryZones()
            draft.documentType = prefs.getString("document_type", "national_id").orEmpty()
            draft.policiesAccepted = prefs.getBoolean("policies", false)
            identityPath = prefs.getString("identity_path", identityPath)
            step = prefs.getInt("step", 1).coerceIn(1, 4)
            return
        }

        draft.businessName = profile?.business_name.orEmpty()
        draft.description = profile?.description.orEmpty()
        draft.phone = profile?.phone.orEmpty()
        draft.whatsapp = profile?.whatsapp.orEmpty()
        draft.whatsappSameAsPhone = draft.phone.isNotBlank() && draft.phone == draft.whatsapp
        draft.requestedCategory = profile?.requested_category.orEmpty()
        draft.categoryId = if (draft.requestedCategory.isNotBlank()) {
            ""
        } else {
            profile?.category_id.orEmpty()
        }
        draft.storeName = profile?.store_name ?: profile?.business_name.orEmpty()
        draft.storeDescription = profile?.store_description ?: profile?.description.orEmpty()
        draft.city = profile?.city?.takeIf { it.isNotBlank() } ?: getString(R.string.merchant_onboarding_10)
        draft.area = profile?.area.orEmpty()
        draft.deliveryZones = profile?.delivery_zones
            ?.takeIf { it.isNotEmpty() }
            ?.map { DeliveryZoneDraft(it.area, it.fee.toString(), it.estimated_minutes?.toString().orEmpty()) }
            ?.toMutableList()
            ?: mutableListOf(
                DeliveryZoneDraft(
                    profile?.delivery_area.orEmpty(),
                    profile?.delivery_fee?.toString().orEmpty(),
                    profile?.estimated_minutes?.toString().orEmpty()
                )
            )
        draft.policiesAccepted = profile?.policies_accepted_at != null
    }

    private fun saveDraft() {
        prefs.edit {
            putBoolean("has_draft", true)
            putString("business_name", draft.businessName)
            putString("description", draft.description)
            putString("phone", draft.phone)
            putString("whatsapp", draft.whatsapp)
            putBoolean("whatsapp_same", draft.whatsappSameAsPhone)
            putString("category_id", draft.categoryId)
            putString("requested_category", draft.requestedCategory)
            putString("store_name", draft.storeName)
            putString("store_description", draft.storeDescription)
            putString("city", draft.city)
            putString("area", draft.area)
            putString("delivery_zones", deliveryZonesJson())
            putString("document_type", draft.documentType)
            putString("identity_path", identityPath)
            putBoolean("policies", draft.policiesAccepted)
            putInt("step", step)
        }
    }

    private fun renderStep() {
        setBusy(false)
        formVisible = true
        content.removeAllViews()
        inputs.clear()
        categoryField = null
        documentField = null
        policiesCheck = null
        identityStatus = null
        whatsappSameCheck = null

        progress.visibility = View.VISIBLE
        stepText.visibility = View.VISIBLE
        progress.max = 4
        progress.setProgressCompat(step, true)
        stepText.text = getString(R.string.merchant_onboarding_11, step.toString())
        backButton.visibility = View.VISIBLE
        nextButton.visibility = View.VISIBLE
        nextButton.text = if (step == 4) getString(R.string.merchant_onboarding_12) else getString(R.string.merchant_onboarding_13)

        reviewNotice?.let(::addReviewNotice)

        when (step) {
            1 -> renderBusinessStep()
            2 -> renderStoreStep()
            3 -> renderDeliveryIdentityStep()
            else -> renderReviewStep()
        }
    }

    private fun addReviewNotice(message: String) {
        val card = card().apply {
            strokeColor = ContextCompat.getColor(requireContext(), R.color.warning)
        }
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(sectionText(getString(R.string.merchant_onboarding_14)))
            addView(smallText(message).apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
            })
        }
        card.addView(body)
        content.addView(card, matchWrapWithMargins())
    }

    private fun renderBusinessStep() {
        title.text = getString(R.string.merchant_onboarding_15)
        addInfoText(getString(R.string.merchant_onboarding_16))
        addCard {
            addField(this, "business_name", getString(R.string.merchant_onboarding_17), draft.businessName)
            addField(this, "description", getString(R.string.merchant_onboarding_18), draft.description, multiline = true, optional = true)
            addField(this, "phone", getString(R.string.merchant_onboarding_19), draft.phone, phone = true)
            addField(this, "whatsapp", getString(R.string.merchant_onboarding_20), draft.whatsapp, phone = true, optional = true)
            whatsappSameCheck = MaterialCheckBox(requireContext()).apply {
                text = getString(R.string.merchant_onboarding_21)
                isChecked = draft.whatsappSameAsPhone
                setOnCheckedChangeListener { _, checked ->
                    draft.whatsappSameAsPhone = checked
                    val whatsapp = inputs["whatsapp"]
                    if (checked) {
                        whatsapp?.setText(inputs["phone"]?.text?.toString().orEmpty())
                        whatsapp?.isEnabled = false
                    } else {
                        whatsapp?.isEnabled = true
                    }
                }
            }
            addView(whatsappSameCheck, matchWrapWithBottomMargin())
            if (draft.whatsappSameAsPhone) inputs["whatsapp"]?.isEnabled = false
        }
    }

    private fun renderStoreStep() {
        title.text = getString(R.string.merchant_onboarding_22)
        addInfoText(getString(R.string.merchant_onboarding_23))
        if (categories.isEmpty()) {
            addInfoText(getString(R.string.merchant_onboarding_24))
        }
        addCard {
            addField(this, "store_name", getString(R.string.merchant_onboarding_25), draft.storeName)
            addDropdown(
                this,
                label = getString(R.string.merchant_onboarding_26),
                labels = categories.map { it.name } + getString(R.string.merchant_onboarding_27),
                selected = categories.firstOrNull { it.id == draft.categoryId }?.name.orEmpty()
            ) { index ->
                draft.categoryId = categories.getOrNull(index)?.id.orEmpty()
                if (index == categories.size) inputs["requested_category"]?.requestFocus()
            }
            addField(
                this,
                "requested_category",
                getString(R.string.merchant_onboarding_28),
                draft.requestedCategory,
                optional = true
            )
            addField(this, "store_description", getString(R.string.merchant_onboarding_29), draft.storeDescription, multiline = true, optional = true)
            addField(this, "city", getString(R.string.merchant_onboarding_30), draft.city)
            addField(this, "area", getString(R.string.merchant_onboarding_31), draft.area, optional = true)
        }
    }

    private fun renderDeliveryIdentityStep() {
        title.text = getString(R.string.merchant_onboarding_32)
        addInfoText(getString(R.string.merchant_onboarding_33))
        draft.deliveryZones.forEachIndexed { index, zone ->
            addCard {
                addView(sectionText(getString(R.string.merchant_onboarding_34, (index + 1).toString())))
                addField(this, "zone_area_$index", getString(R.string.merchant_onboarding_35), zone.area)
                addField(this, "zone_fee_$index", getString(R.string.merchant_onboarding_36), zone.fee, decimal = true)
                addField(
                    this,
                    "zone_eta_$index",
                    getString(R.string.merchant_onboarding_37),
                    zone.estimatedMinutes,
                    number = true,
                    optional = true
                )
                if (draft.deliveryZones.size > 1) {
                    addView(MaterialButton(requireContext()).apply {
                        text = getString(R.string.merchant_onboarding_38)
                        isAllCaps = false
                        setOnClickListener {
                            captureDeliveryZones()
                            draft.deliveryZones.removeAt(index)
                            saveDraft()
                            renderStep()
                        }
                    }, matchWrap())
                }
            }
        }
        addSecondaryButton(getString(R.string.merchant_onboarding_39)) {
            captureDeliveryZones()
            draft.deliveryZones.add(DeliveryZoneDraft())
            saveDraft()
            renderStep()
        }
        addIdentityCard()
    }

    private fun renderReviewStep() {
        title.text = getString(R.string.merchant_onboarding_40)
        addInfoText(getString(R.string.merchant_onboarding_41))

        addSummaryCard(getString(R.string.merchant_onboarding_42), listOf(
            getString(R.string.merchant_onboarding_17) to draft.businessName,
            getString(R.string.merchant_onboarding_43) to draft.phone,
            getString(R.string.merchant_onboarding_44) to draft.whatsapp.ifBlank { "—" }
        ))
        addSummaryCard(getString(R.string.main_18), listOf(
            getString(R.string.merchant_onboarding_25) to draft.storeName,
            getString(R.string.merchant_onboarding_45) to (categories.firstOrNull { it.id == draft.categoryId }?.name
                ?: draft.requestedCategory.ifBlank { "—" }),
            getString(R.string.merchant_onboarding_46) to listOf(draft.city, draft.area).filter { it.isNotBlank() }.joinToString(" - ")
        ))
        addSummaryCard(getString(R.string.merchant_onboarding_47), listOf(
            getString(R.string.merchant_onboarding_48) to draft.deliveryZones.joinToString(" • ") {
                "${it.area}: ${it.fee} جنيه${it.estimatedMinutes.takeIf(String::isNotBlank)?.let { eta -> "، $eta دقيقة بعد التسليم للمندوب" } ?: ""}"
            },
            getString(R.string.merchant_onboarding_49) to if (identityPath.isNullOrBlank()) getString(R.string.merchant_onboarding_50) else getString(R.string.merchant_onboarding_51)
        ))

        addSecondaryButton(getString(R.string.merchant_onboarding_52)) {
            draft.policiesAccepted = policiesCheck?.isChecked ?: draft.policiesAccepted
            saveDraft()
            (activity as? MainActivity)?.show(PoliciesFragment())
        }

        policiesCheck = MaterialCheckBox(requireContext()).apply {
            text = getString(R.string.merchant_onboarding_53)
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
        body.addView(sectionText(getString(R.string.merchant_onboarding_54)))
        body.addView(smallText(getString(R.string.merchant_onboarding_55)))

        val docLabels = listOf(getString(R.string.merchant_onboarding_56), getString(R.string.merchant_onboarding_57), getString(R.string.merchant_onboarding_58))
        val docValues = listOf("national_id", "passport", "other")
        addDropdown(
            body,
            getString(R.string.merchant_onboarding_59),
            docLabels,
            docLabels.getOrNull(docValues.indexOf(draft.documentType)).orEmpty()
        ) { index -> draft.documentType = docValues.getOrElse(index) { "national_id" } }
        documentField = body.findViewWithTag("dropdown")

        identityStatus = smallText(if (identityPath.isNullOrBlank()) getString(R.string.merchant_onboarding_60) else getString(R.string.merchant_onboarding_61)).apply {
            setTextColor(ContextCompat.getColor(requireContext(), if (identityPath.isNullOrBlank()) R.color.text_muted else R.color.success))
            setPadding(0, dp(8), 0, dp(8))
        }
        body.addView(identityStatus)
        val upload = MaterialButton(requireContext()).apply {
            text = if (identityPath.isNullOrBlank()) getString(R.string.merchant_onboarding_62) else getString(R.string.merchant_onboarding_63)
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
        if (submissionInFlight) return
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
                draft.whatsappSameAsPhone = whatsappSameCheck?.isChecked ?: draft.whatsappSameAsPhone
                draft.whatsapp = if (draft.whatsappSameAsPhone) draft.phone else value("whatsapp")
            }
            2 -> {
                draft.storeName = value("store_name")
                draft.requestedCategory = value("requested_category")
                draft.storeDescription = value("store_description")
                draft.city = value("city")
                draft.area = value("area")
            }
            3 -> captureDeliveryZones()
            4 -> draft.policiesAccepted = policiesCheck?.isChecked ?: draft.policiesAccepted
        }
    }

    private fun captureDeliveryZones() {
        draft.deliveryZones.forEachIndexed { index, zone ->
            zone.area = value("zone_area_$index")
            zone.fee = value("zone_fee_$index")
            zone.estimatedMinutes = value("zone_eta_$index")
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
                    draft.businessName.trim().length < 2 -> fail(getString(R.string.merchant_onboarding_64))
                    !Regex("^\\+?[0-9]{7,15}$").matches(cleanPhone) -> fail(getString(R.string.merchant_onboarding_65))
                    else -> true
                }
            }
            2 -> when {
                draft.storeName.trim().length < 2 -> fail(getString(R.string.merchant_onboarding_66))
                draft.categoryId.isBlank() && draft.requestedCategory.trim().length < 2 -> fail(getString(R.string.merchant_onboarding_67))
                draft.city.trim().length < 2 -> fail(getString(R.string.merchant_onboarding_68))
                else -> true
            }
            3 -> {
                draft.deliveryZones.forEachIndexed { index, zone ->
                    val fee = zone.fee.toDoubleOrNull()
                    val minutes = zone.estimatedMinutes.takeIf { it.isNotBlank() }?.toIntOrNull()
                    when {
                        zone.area.trim().length < 2 -> return fail(getString(R.string.merchant_onboarding_69, (index + 1).toString()))
                        fee == null || fee < 0 -> return fail(getString(R.string.merchant_onboarding_70, (index + 1).toString()))
                        zone.estimatedMinutes.isNotBlank() && (minutes == null || minutes !in 1..1440) -> return fail(getString(R.string.merchant_onboarding_71, (index + 1).toString()))
                    }
                }
                when {
                    draft.deliveryZones.isEmpty() -> fail(getString(R.string.merchant_onboarding_72))
                    identityPath.isNullOrBlank() -> fail(getString(R.string.merchant_onboarding_73))
                    else -> true
                }
            }
            else -> if (!draft.policiesAccepted) fail(getString(R.string.merchant_onboarding_74)) else true
        }
    }

    private fun submit() {
        if (submissionInFlight) return
        val identity = identityPath ?: return
        val category = draft.categoryId.takeIf { it.isNotBlank() }
        val zones = draft.deliveryZones.map { zone ->
            MerchantDeliveryZoneInput(
                area = zone.area,
                fee = zone.fee.toDoubleOrNull() ?: return,
                estimated_minutes = zone.estimatedMinutes.toIntOrNull()
            )
        }
        submissionInFlight = true
        setBusy(true)
        submitStatus.text = getString(R.string.merchant_onboarding_75)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                repository.submitMerchantApplication(
                    businessName = draft.businessName,
                    description = draft.description,
                    phone = draft.phone,
                    whatsapp = draft.whatsapp,
                    categoryId = category,
                    requestedCategory = draft.requestedCategory,
                    storeName = draft.storeName,
                    storeDescription = draft.storeDescription,
                    city = draft.city,
                    area = draft.area,
                    deliveryZones = zones,
                    identityPath = identity,
                    documentType = draft.documentType,
                    acceptPolicies = draft.policiesAccepted
                )
            }.onSuccess {
                submissionInFlight = false
                prefs.edit { clear() }
                reviewNotice = null
                renderSubmittedState()
                Toast.makeText(requireContext(), getString(R.string.merchant_onboarding_76), Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                submissionInFlight = false
                setBusy(false)
                nextButton.text = getString(R.string.merchant_onboarding_12)
                Toast.makeText(
                    requireContext(),
                    merchantError(error, getString(R.string.merchant_onboarding_77)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderSubmittedState() {
        formVisible = false
        setBusy(false)
        content.removeAllViews()
        progress.visibility = View.GONE
        stepText.visibility = View.GONE
        backButton.visibility = View.GONE
        nextButton.visibility = View.GONE
        title.text = getString(R.string.merchant_onboarding_78)
        addCard {
            addView(sectionText(getString(R.string.merchant_onboarding_79)))
            addView(smallText(getString(R.string.merchant_onboarding_80)))
        }
        addSecondaryButton(getString(R.string.merchant_onboarding_81)) { loadEntry() }
    }

    private fun renderStatus(profile: MerchantProfile, headline: String, approved: Boolean = false) {
        formVisible = false
        setBusy(false)
        content.removeAllViews()
        progress.visibility = View.GONE
        stepText.visibility = View.GONE
        backButton.visibility = View.GONE
        nextButton.visibility = View.GONE
        title.text = getString(R.string.merchant_onboarding_82)
        addCard {
            addView(sectionText(headline))
            addView(summaryRow(getString(R.string.merchant_onboarding_42), profile.business_name))
            addView(summaryRow(getString(R.string.main_18), profile.store_name ?: "—"))
            addView(summaryRow(getString(R.string.merchant_onboarding_43), profile.phone ?: "—"))
            addView(summaryRow(getString(R.string.merchant_onboarding_83), statusText(profile.verification_status)))
            profile.review_note?.takeIf { it.isNotBlank() }?.let { addView(summaryRow(getString(R.string.merchant_onboarding_84), it)) }
        }
        if (approved) {
            addPrimaryButton(getString(R.string.merchant_onboarding_85)) { (activity as? MainActivity)?.show(SellerFragment()) }
        } else {
            addSecondaryButton(getString(R.string.merchant_onboarding_86)) { loadEntry() }
        }
    }

    private fun uploadIdentity(uri: Uri) {
        submitStatus.text = getString(R.string.merchant_onboarding_87)
        setBusy(true)
        identityStatus?.text = getString(R.string.merchant_onboarding_88)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable {
                val pair = withContext(Dispatchers.IO) { identityBytes(uri) }
                repository.uploadMerchantIdentity(pair.first, pair.second)
            }.onSuccess { path ->
                identityPath = path
                saveDraft()
                setBusy(false)
                Toast.makeText(requireContext(), getString(R.string.merchant_onboarding_61), Toast.LENGTH_SHORT).show()
                renderStep()
            }.onFailure { error ->
                setBusy(false)
                Toast.makeText(
                    requireContext(),
                    merchantError(error, getString(R.string.merchant_onboarding_89)),
                    Toast.LENGTH_LONG
                ).show()
                renderStep()
            }
        }
    }

    private fun identityBytes(uri: Uri): Pair<ByteArray, String> {
        val mime = requireContext().contentResolver.getType(uri).orEmpty()
        if (mime == "application/pdf") {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error(getString(R.string.merchant_onboarding_90))
            require(bytes.size <= 8 * 1024 * 1024) { getString(R.string.merchant_onboarding_91) }
            return bytes to "application/pdf"
        }
        val bitmap = requireContext().contentResolver.decodeBoundedBitmap(uri, 1600)
            ?: error(getString(R.string.account_4))
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > 1600) {
            val ratio = 1600f / largest
            bitmap.scale(
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1)
            )
        } else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            require(scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)) { getString(R.string.account_5) }
            out.toByteArray()
        }
        require(bytes.size <= 5 * 1024 * 1024) { getString(R.string.merchant_onboarding_92) }
        return bytes to "image/jpeg"
    }

    private fun merchantError(error: Throwable, fallback: String): String {
        val raw = error.message.orEmpty()
        val specific = when {
            raw.contains("Identity document was not uploaded", ignoreCase = true) ||
                raw.contains("Identity document is required", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_93)
            raw.contains("Store category is invalid", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_94)
            raw.contains("Choose a category", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_95)
            raw.contains("At least one delivery zone", ignoreCase = true) ||
                raw.contains("Invalid delivery", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_96)
            raw.contains("Merchant policies", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_97)
            raw.contains("cannot be resubmitted", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_98)
            raw.contains("Authentication required", ignoreCase = true) ->
                getString(R.string.merchant_onboarding_99)
            else -> return CustomerErrorMessages.from(error, fallback)
        }
        return CustomerErrorMessages.from(error, specific)
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
            helperText = if (optional) getString(R.string.merchant_onboarding_100) else null
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
        if (label == getString(R.string.merchant_onboarding_26)) categoryField = field
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
        text = getString(R.string.merchant_onboarding_105, label, value.ifBlank { "—" })
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
        textSize = 22f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
        setPadding(0, 0, 0, dp(8))
    }

    private fun smallText(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 14f
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
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
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
        submitProgress.visibility = if (busy) View.VISIBLE else View.GONE
        submitStatus.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy && submitStatus.text.isNullOrBlank()) submitStatus.text = getString(R.string.merchant_onboarding_101)
    }

    private fun deliveryZonesJson(): String = JSONArray().apply {
        draft.deliveryZones.forEach { zone ->
            put(JSONObject().apply {
                put("area", zone.area)
                put("fee", zone.fee)
                put("estimated_minutes", zone.estimatedMinutes)
            })
        }
    }.toString()

    private fun restoreDeliveryZones(): MutableList<DeliveryZoneDraft> {
        val raw = prefs.getString("delivery_zones", null)
        if (!raw.isNullOrBlank()) {
            val restored = runCatchingCancellable {
                val array = JSONArray(raw)
                MutableList(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    DeliveryZoneDraft(
                        area = item.optString("area"),
                        fee = item.optString("fee"),
                        estimatedMinutes = item.optString("estimated_minutes")
                    )
                }
            }.getOrNull()
            if (!restored.isNullOrEmpty()) return restored
        }
        return mutableListOf(
            DeliveryZoneDraft(
                area = prefs.getString("delivery_area", "").orEmpty(),
                fee = prefs.getString("delivery_fee", "").orEmpty(),
                estimatedMinutes = prefs.getString("estimated_minutes", "").orEmpty()
            )
        )
    }

    private fun value(key: String): String = inputs[key]?.text?.toString()?.trim().orEmpty()

    private fun statusText(status: String): String = when (status) {
        "pending" -> getString(R.string.monetization_32)
        "approved" -> getString(R.string.merchant_onboarding_102)
        "changes_requested" -> getString(R.string.merchant_onboarding_103)
        "rejected" -> getString(R.string.monetization_34)
        "suspended" -> getString(R.string.merchant_onboarding_104)
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
        var whatsappSameAsPhone: Boolean = false,
        var categoryId: String = "",
        var requestedCategory: String = "",
        var storeName: String = "",
        var storeDescription: String = "",
        var city: String = "كوستي",
        var area: String = "",
        var deliveryZones: MutableList<DeliveryZoneDraft> = mutableListOf(DeliveryZoneDraft()),
        var documentType: String = "national_id",
        var policiesAccepted: Boolean = false
    )

    private data class DeliveryZoneDraft(
        var area: String = "",
        var fee: String = "",
        var estimatedMinutes: String = ""
    )
}
