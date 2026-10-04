package com.tani.app.ui.profile

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

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
import com.tani.app.data.Supabase
import com.tani.app.data.cache.AppContentStore
import com.tani.app.data.cache.MarketplaceCache
import com.tani.app.ui.commerce.AddressesFragment
import com.tani.app.ui.growth.ReferralFragment
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val repository = Repository()
    private lateinit var logoutButton: Button
    private lateinit var deleteButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logoutButton = view.findViewById(R.id.profile_logout)
        deleteButton = view.findViewById(R.id.profile_delete_account)

        view.findViewById<Button>(R.id.profile_account).setOnClickListener {
            (activity as? MainActivity)?.show(AccountFragment())
        }
        view.findViewById<Button>(R.id.profile_addresses).setOnClickListener {
            (activity as? MainActivity)?.show(AddressesFragment())
        }
        view.findViewById<Button>(R.id.profile_referrals).setOnClickListener {
            (activity as? MainActivity)?.show(ReferralFragment())
        }
        view.findViewById<Button>(R.id.profile_change_password).setOnClickListener {
            showChangePasswordDialog()
        }
        deleteButton.setOnClickListener { confirmDeleteAccount() }
        logoutButton.setOnClickListener { confirmLogout() }
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
        val current = passwordField(getString(R.string.profile_1))
        val next = passwordField(getString(R.string.profile_2))
        val confirm = passwordField(getString(R.string.profile_3))

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_4))
            .setMessage(getString(R.string.profile_5))
            .setView(container)
            .setPositiveButton(getString(R.string.addresses_18)) { _, _ ->
                setActionsEnabled(false)
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatchingCancellable {
                        repository.changePassword(
                            currentPassword = current.text.toString(),
                            newPassword = next.text.toString(),
                            confirmPassword = confirm.text.toString()
                        )
                    }.onSuccess {
                        Toast.makeText(requireContext(), getString(R.string.profile_6), Toast.LENGTH_LONG).show()
                    }.onFailure {
                        val message = if (it.message.orEmpty().contains("password", ignoreCase = true)) {
                            getString(R.string.profile_7)
                        } else CustomerErrorMessages.from(it, getString(R.string.profile_8))
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                    setActionsEnabled(true)
                }
            }
            .setNegativeButton(getString(R.string.addresses_19), null)
            .show()
    }

    private fun confirmDeleteAccount() {
        val confirmation = EditText(requireContext()).apply { hint = getString(R.string.profile_9) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_10))
            .setMessage(
                getString(R.string.profile_11)
            )
            .setView(confirmation)
            .setPositiveButton(getString(R.string.cart_13)) { _, _ ->
                if (confirmation.text.toString().trim() != getString(R.string.cart_13)) {
                    Toast.makeText(requireContext(), getString(R.string.profile_9), Toast.LENGTH_LONG).show()
                } else {
                    deleteAccount()
                }
            }
            .setNegativeButton(getString(R.string.addresses_19), null)
            .show()
    }

    private fun deleteAccount() {
        val appContext = requireContext().applicationContext
        val userId = Supabase.userId
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.softDeleteAccount() }
                .onSuccess { deleted ->
                    if (deleted) {
                        AppContentStore.clearPrivateData()
                        MarketplaceCache(appContext).clearRecommendations(userId)
                    }
                    if (deleted && isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.profile_12), Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.showAuth()
                    } else if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.profile_13), Toast.LENGTH_LONG).show()
                        setActionsEnabled(true)
                    }
                }
                .onFailure {
                    if (isAdded) {
                        Toast.makeText(requireContext(), CustomerErrorMessages.from(it, getString(R.string.profile_13)), Toast.LENGTH_LONG).show()
                        setActionsEnabled(true)
                    }
                }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_14))
            .setMessage(getString(R.string.profile_15))
            .setPositiveButton(getString(R.string.profile_14)) { _, _ -> logout() }
            .setNegativeButton(getString(R.string.addresses_19), null)
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
        logoutButton.isEnabled = enabled
        deleteButton.isEnabled = enabled
    }
}
