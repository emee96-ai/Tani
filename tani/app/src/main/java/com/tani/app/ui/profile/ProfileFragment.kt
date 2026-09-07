package com.tani.app.ui.profile

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.data.Seller
import com.tani.app.data.MerchantProfile
import com.tani.app.ui.seller.SellerFragment
import com.tani.app.ui.commerce.AddressesFragment
import com.tani.app.ui.growth.FavoritesFragment
import com.tani.app.ui.growth.NotificationsFragment
import com.tani.app.ui.growth.ReferralFragment
import com.tani.app.ui.trust.SupportCenterFragment
import com.tani.app.ui.legal.PoliciesFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val repository = Repository()

    private lateinit var progress: ProgressBar
    private lateinit var content: LinearLayout
    private lateinit var avatar: ImageView
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var role: TextView
    private lateinit var sellerStatus: TextView
    private lateinit var sellerButton: Button
    private lateinit var dashboardButton: Button
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button
    private lateinit var changeAvatarButton: Button

    private var selectedAvatarBytes: ByteArray? = null
    private var currentAvatarUrl: String? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) prepareAvatar(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress = view.findViewById(R.id.profile_progress)
        content = view.findViewById(R.id.profile_content)
        avatar = view.findViewById(R.id.profile_avatar)
        name = view.findViewById(R.id.profile_name)
        email = view.findViewById(R.id.profile_email)
        phone = view.findViewById(R.id.profile_phone)
        role = view.findViewById(R.id.profile_role)
        sellerStatus = view.findViewById(R.id.profile_seller_status)
        sellerButton = view.findViewById(R.id.profile_seller)
        dashboardButton = view.findViewById(R.id.profile_dashboard)
        saveButton = view.findViewById(R.id.profile_save)
        logoutButton = view.findViewById(R.id.profile_logout)
        changeAvatarButton = view.findViewById(R.id.profile_change_avatar)

        changeAvatarButton.setOnClickListener { pickAvatar.launch("image/*") }
        view.findViewById<Button>(R.id.profile_addresses).setOnClickListener {
            (activity as? MainActivity)?.show(AddressesFragment())
        }
        view.findViewById<Button>(R.id.profile_favorites).setOnClickListener { (activity as? MainActivity)?.show(FavoritesFragment()) }
        view.findViewById<Button>(R.id.profile_notifications).setOnClickListener { (activity as? MainActivity)?.show(NotificationsFragment()) }
        view.findViewById<Button>(R.id.profile_referrals).setOnClickListener { (activity as? MainActivity)?.show(ReferralFragment()) }
        view.findViewById<Button>(R.id.profile_support).setOnClickListener { (activity as? MainActivity)?.show(SupportCenterFragment()) }
        view.findViewById<Button>(R.id.profile_policies).setOnClickListener { (activity as? MainActivity)?.show(PoliciesFragment()) }
        view.findViewById<Button>(R.id.profile_delete_account).setOnClickListener { confirmDeleteAccount() }
        saveButton.setOnClickListener { saveProfile() }
        logoutButton.setOnClickListener { confirmLogout() }
        dashboardButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, SellerFragment())
                .addToBackStack(null)
                .commit()
        }
        sellerButton.setOnClickListener { openSeller() }

        loadProfile()
    }

    private fun loadProfile() {
        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                val account = repository.accountProfile()
                val merchant = repository.merchantProfile()
                val seller = repository.seller()
                Triple(account, merchant, seller)
            }.onSuccess { (account, merchant, seller) ->
                currentAvatarUrl = account.profile.avatar_url
                name.setText(account.profile.name)
                email.setText(account.email)
                phone.setText(account.profile.phone)
                role.text = when (account.profile.role) {
                    "admin" -> "نوع الحساب: إدارة"
                    "seller" -> "نوع الحساب: تاجر"
                    else -> "نوع الحساب: عميل"
                }
                updateSellerState(merchant, seller)
                account.profile.avatar_url?.takeIf { it.isNotBlank() }?.let(::loadRemoteAvatar)
                setLoading(false)
            }.onFailure {
                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    it.message ?: "تعذر تحميل الملف الشخصي",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveProfile() {
        setActionsEnabled(false)
        lifecycleScope.launch {
            runCatching {
                val uploadedAvatar = selectedAvatarBytes?.let { repository.uploadAvatar(it) }
                repository.updateProfile(
                    name = name.text.toString(),
                    phone = phone.text.toString(),
                    avatarUrl = uploadedAvatar
                )
            }.onSuccess { updated ->
                currentAvatarUrl = updated.avatar_url
                selectedAvatarBytes = null
                name.setText(updated.name)
                phone.setText(updated.phone)
                Toast.makeText(requireContext(), "تم حفظ التعديلات", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    it.message ?: "تعذر حفظ التعديلات",
                    Toast.LENGTH_LONG
                ).show()
            }
            setActionsEnabled(true)
        }
    }

    private fun prepareAvatar(uri: Uri) {
        setActionsEnabled(false)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = requireContext().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: error("تعذر قراءة الصورة")

                    val scaled = scaleDown(bitmap, 1024)
                    ByteArrayOutputStream().use { output ->
                        require(scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                            "تعذر تجهيز الصورة"
                        }
                        output.toByteArray()
                    }.also {
                        require(it.size <= 5 * 1024 * 1024) {
                            "حجم الصورة كبير جداً. اختاري صورة أصغر"
                        }
                    }
                }
            }.onSuccess { bytes ->
                selectedAvatarBytes = bytes
                avatar.setPadding(0, 0, 0, 0)
                avatar.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    it.message ?: "تعذر تجهيز الصورة",
                    Toast.LENGTH_LONG
                ).show()
            }
            setActionsEnabled(true)
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / largest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun loadRemoteAvatar(url: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null && isAdded) {
                avatar.setPadding(0, 0, 0, 0)
                avatar.setImageBitmap(bitmap)
            }
        }
    }

    private fun updateSellerState(merchant: MerchantProfile?, seller: Seller?) {
        if (merchant == null) {
            sellerStatus.text = "حسابك حالياً حساب عميل. يمكنك بدء مسار التحقق كتاجر."
            sellerButton.visibility = View.VISIBLE
            sellerButton.isEnabled = true
            sellerButton.text = "ابدئي البيع على تاني"
            dashboardButton.visibility = View.GONE
            return
        }

        when (merchant.verification_status) {
            "approved" -> {
                sellerStatus.text = "المتجر: ${merchant.store_name ?: seller?.store_name ?: merchant.business_name} — موثق ومفعّل"
                sellerButton.visibility = View.GONE
                dashboardButton.visibility = View.VISIBLE
            }
            "changes_requested" -> {
                sellerStatus.text = "طلب التاجر يحتاج تعديلات${merchant.review_note?.let { ": $it" } ?: ""}"
                sellerButton.visibility = View.VISIBLE
                sellerButton.isEnabled = true
                sellerButton.text = "تعديل طلب التاجر"
                dashboardButton.visibility = View.GONE
            }
            "rejected" -> {
                sellerStatus.text = "تم رفض طلب التاجر ويمكن تعديل البيانات وإعادة الإرسال."
                sellerButton.visibility = View.VISIBLE
                sellerButton.isEnabled = true
                sellerButton.text = "مراجعة وإعادة الإرسال"
                dashboardButton.visibility = View.GONE
            }
            "suspended" -> {
                sellerStatus.text = "حساب التاجر موقوف مؤقتاً${merchant.review_note?.let { ": $it" } ?: ""}"
                sellerButton.visibility = View.VISIBLE
                sellerButton.isEnabled = true
                sellerButton.text = "عرض حالة حساب التاجر"
                dashboardButton.visibility = View.GONE
            }
            else -> {
                sellerStatus.text = "طلب التاجر قيد المراجعة"
                sellerButton.visibility = View.VISIBLE
                sellerButton.isEnabled = true
                sellerButton.text = "عرض حالة الطلب"
                dashboardButton.visibility = View.GONE
            }
        }
    }

    private fun openSeller() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host, SellerFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun showMerchantOnboardingNotice() {
        AlertDialog.Builder(requireContext())
            .setTitle("التسجيل كتاجر")
            .setMessage(
                "حسب نظام تاني، تسجيل التاجر يتطلب التحقق من الهاتف والهوية وبيانات النشاط والمتجر والتوصيل قبل المراجعة والاعتماد."
            )
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun confirmDeleteAccount() {
        val confirmation = EditText(requireContext()).apply { hint = "اكتبي كلمة حذف للتأكيد" }
        AlertDialog.Builder(requireContext())
            .setTitle("حذف الحساب")
            .setMessage("سيتم تعطيل الحساب وإخفاء أي متجر ومنتجات نشطة. نحتفظ بالحد الأدنى من سجلات الطلبات اللازمة للدعم والمحاسبة والأمان. لا يمكن التراجع من داخل التطبيق.")
            .setView(confirmation)
            .setPositiveButton("حذف") { _, _ ->
                if (confirmation.text.toString().trim() != "حذف") {
                    Toast.makeText(requireContext(), "اكتبي كلمة حذف للتأكيد", Toast.LENGTH_LONG).show()
                } else deleteAccount()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteAccount() {
        setActionsEnabled(false)
        lifecycleScope.launch {
            runCatching { repository.softDeleteAccount() }
                .onSuccess { deleted ->
                    if (deleted && isAdded) {
                        Toast.makeText(requireContext(), "تم تعطيل الحساب", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.showAuth()
                    } else {
                        Toast.makeText(requireContext(), "تعذر حذف الحساب", Toast.LENGTH_LONG).show()
                        setActionsEnabled(true)
                    }
                }
                .onFailure {
                    Toast.makeText(requireContext(), it.message ?: "تعذر حذف الحساب", Toast.LENGTH_LONG).show()
                    setActionsEnabled(true)
                }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("تسجيل الخروج")
            .setMessage("هل تريدين تسجيل الخروج من حسابك؟")
            .setPositiveButton("تسجيل الخروج") { _, _ -> logout() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun logout() {
        setActionsEnabled(false)
        lifecycleScope.launch {
            repository.logout()
            if (isAdded) (activity as? MainActivity)?.showAuth()
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        content.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun setActionsEnabled(enabled: Boolean) {
        saveButton.isEnabled = enabled
        logoutButton.isEnabled = enabled
        changeAvatarButton.isEnabled = enabled
    }
}
