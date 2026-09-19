package com.tani.app.ui.profile

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.data.MerchantProfile
import com.tani.app.data.Supabase
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.ui.growth.FavoritesFragment
import com.tani.app.ui.growth.NotificationsFragment
import com.tani.app.ui.growth.ReferralFragment
import com.tani.app.ui.legal.AboutFragment
import com.tani.app.ui.legal.PoliciesFragment
import com.tani.app.ui.seller.MerchantOnboardingFragment
import com.tani.app.ui.seller.SellerFragment
import com.tani.app.ui.trust.SupportCenterFragment
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val repository = Repository()
    private lateinit var dashboardButton: Button
    private lateinit var logoutButton: Button
    private lateinit var deleteButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dashboardButton = view.findViewById(R.id.profile_dashboard)
        logoutButton = view.findViewById(R.id.profile_logout)
        deleteButton = view.findViewById(R.id.profile_delete_account)

        view.findViewById<Button>(R.id.profile_account).setOnClickListener {
            (activity as? MainActivity)?.show(AccountFragment())
        }
        dashboardButton.setOnClickListener { openStoreDashboard() }
        view.findViewById<Button>(R.id.profile_favorites).setOnClickListener {
            (activity as? MainActivity)?.show(FavoritesFragment())
        }
        view.findViewById<Button>(R.id.profile_notifications).setOnClickListener {
            (activity as? MainActivity)?.show(NotificationsFragment())
        }
        view.findViewById<Button>(R.id.profile_referrals).setOnClickListener {
            (activity as? MainActivity)?.show(ReferralFragment())
        }
        view.findViewById<Button>(R.id.profile_support).setOnClickListener {
            (activity as? MainActivity)?.show(SupportCenterFragment())
        }
        view.findViewById<Button>(R.id.profile_policies).setOnClickListener {
            (activity as? MainActivity)?.show(PoliciesFragment())
        }
        view.findViewById<Button>(R.id.profile_change_password).setOnClickListener {
            showChangePasswordDialog()
        }
        view.findViewById<Button>(R.id.profile_about).setOnClickListener {
            (activity as? MainActivity)?.show(AboutFragment())
        }
        deleteButton.setOnClickListener { confirmDeleteAccount() }
        logoutButton.setOnClickListener { confirmLogout() }
    }

    private fun openStoreDashboard() {
        if (AppContentStore.merchantLoaded) {
            openMerchantDestination(AppContentStore.merchant)
            return
        }
        dashboardButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.merchantProfile() }
                .onSuccess { merchant ->
                    if (!isAdded) return@onSuccess
                    AppContentStore.updateMerchant(merchant)
                    openMerchantDestination(merchant)
                }
                .onFailure {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            it.message ?: "تعذر فتح بيانات المتجر",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            if (isAdded) dashboardButton.isEnabled = true
        }
    }

    private fun openMerchantDestination(merchant: MerchantProfile?) {
        val destination = if (merchant?.verification_status == "approved") {
            SellerFragment()
        } else {
            MerchantOnboardingFragment()
        }
        (activity as? MainActivity)?.show(destination)
    }

    private fun showChangePasswordDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
        }
        fun passwordField(hint: String) = EditText(requireContext()).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            container.addView(this)
        }
        val current = passwordField("كلمة المرور الحالية")
        val next = passwordField("كلمة المرور الجديدة")
        val confirm = passwordField("تأكيد كلمة المرور الجديدة")

        AlertDialog.Builder(requireContext())
            .setTitle("تغيير كلمة المرور")
            .setMessage("استخدمي 8 أحرف على الأقل، وبها حرف ورقم.")
            .setView(container)
            .setPositiveButton("حفظ") { _, _ ->
                setActionsEnabled(false)
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        repository.changePassword(
                            currentPassword = current.text.toString(),
                            newPassword = next.text.toString(),
                            confirmPassword = confirm.text.toString()
                        )
                    }.onSuccess {
                        Toast.makeText(requireContext(), "تم تغيير كلمة المرور", Toast.LENGTH_LONG).show()
                    }.onFailure {
                        val message = if (it.message.orEmpty().contains("password", ignoreCase = true)) {
                            "كلمة المرور الحالية غير صحيحة أو الجديدة لا تحقق شروط الأمان"
                        } else it.message ?: "تعذر تغيير كلمة المرور"
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                    setActionsEnabled(true)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDeleteAccount() {
        val confirmation = EditText(requireContext()).apply { hint = "اكتبي كلمة حذف للتأكيد" }
        AlertDialog.Builder(requireContext())
            .setTitle("حذف الحساب")
            .setMessage(
                "سيتم تعطيل الحساب وإخفاء أي متجر ومنتجات نشطة. نحتفظ بالحد الأدنى من سجلات الطلبات اللازمة للدعم والمحاسبة والأمان. لا يمكن التراجع من داخل التطبيق."
            )
            .setView(confirmation)
            .setPositiveButton("حذف") { _, _ ->
                if (confirmation.text.toString().trim() != "حذف") {
                    Toast.makeText(requireContext(), "اكتبي كلمة حذف للتأكيد", Toast.LENGTH_LONG).show()
                } else {
                    deleteAccount()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteAccount() {
        val appContext = requireContext().applicationContext
        val userId = Supabase.userId
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.softDeleteAccount() }
                .onSuccess { deleted ->
                    if (deleted) {
                        AppContentStore.clearPrivateData()
                        MarketplaceCache(appContext).clearRecommendations(userId)
                    }
                    if (deleted && isAdded) {
                        Toast.makeText(requireContext(), "تم تعطيل الحساب", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.showAuth()
                    } else if (isAdded) {
                        Toast.makeText(requireContext(), "تعذر حذف الحساب", Toast.LENGTH_LONG).show()
                        setActionsEnabled(true)
                    }
                }
                .onFailure {
                    if (isAdded) {
                        Toast.makeText(requireContext(), it.message ?: "تعذر حذف الحساب", Toast.LENGTH_LONG).show()
                        setActionsEnabled(true)
                    }
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
        val appContext = requireContext().applicationContext
        val userId = Supabase.userId
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.logout()
            AppContentStore.clearPrivateData()
            MarketplaceCache(appContext).clearRecommendations(userId)
            if (isAdded) (activity as? MainActivity)?.showAuth()
        }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        dashboardButton.isEnabled = enabled
        logoutButton.isEnabled = enabled
        deleteButton.isEnabled = enabled
    }
}
