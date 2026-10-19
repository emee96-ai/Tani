package com.tani.app.ui.auth

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Cart
import com.tani.app.data.Repository
import com.tani.app.ui.commerce.CheckoutFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthFragment : Fragment(R.layout.fragment_auth) {

    private enum class Mode { LOGIN, SIGNUP, FORGOT, RESET }

    private val repository = Repository()
    private var mode = Mode.LOGIN
    private var recoveryToken: String = ""
    private var recoveryRefreshToken: String = ""

    companion object {
        private const val ARG_RECOVERY_TOKEN = "recovery_token"
        private const val ARG_RECOVERY_REFRESH_TOKEN = "recovery_refresh_token"
        private const val ARG_RECOVERY_ERROR = "recovery_error"
        private const val ARG_NOTICE = "notice"
        private const val ARG_AFTER_AUTH = "after_auth"
        private const val AFTER_AUTH_CHECKOUT = "checkout"
        private const val AFTER_AUTH_MERCHANT = "merchant"

        fun newResetInstance(
            token: String?,
            refreshToken: String?,
            error: String? = null
        ) = AuthFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_RECOVERY_TOKEN, token)
                putString(ARG_RECOVERY_REFRESH_TOKEN, refreshToken)
                putString(ARG_RECOVERY_ERROR, error)
            }
        }

        /** شاشة تسجيل الدخول مع رسالة، مثل تأكيد البريد الإلكتروني. */
        fun newNoticeInstance(notice: String) = AuthFragment().apply {
            arguments = Bundle().apply { putString(ARG_NOTICE, notice) }
        }

        fun newCheckoutInstance() = AuthFragment().apply {
            arguments = Bundle().apply { putString(ARG_AFTER_AUTH, AFTER_AUTH_CHECKOUT) }
        }

        fun newMerchantInstance() = AuthFragment().apply {
            arguments = Bundle().apply { putString(ARG_AFTER_AUTH, AFTER_AUTH_MERCHANT) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.auth_title)
        val subtitle = view.findViewById<TextView>(R.id.auth_subtitle)
        val name = view.findViewById<EditText>(R.id.name)
        val phone = view.findViewById<EditText>(R.id.phone)
        val email = view.findViewById<EditText>(R.id.email)
        val password = view.findViewById<EditText>(R.id.password)
        val confirmPassword = view.findViewById<EditText>(R.id.confirm_password)
        val action = view.findViewById<Button>(R.id.action)
        val forgot = view.findViewById<TextView>(R.id.forgot_password)
        val toggle = view.findViewById<TextView>(R.id.toggle)
        val backToLogin = view.findViewById<TextView>(R.id.back_to_login)
        val browseAsGuest = view.findViewById<TextView>(R.id.browse_as_guest)
        val status = view.findViewById<TextView>(R.id.status)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val afterAuth = arguments?.getString(ARG_AFTER_AUTH)
        val continuingCheckout = afterAuth == AFTER_AUTH_CHECKOUT
        val continuingMerchant = afterAuth == AFTER_AUTH_MERCHANT

        recoveryToken = arguments?.getString(ARG_RECOVERY_TOKEN).orEmpty()
        recoveryRefreshToken = arguments?.getString(ARG_RECOVERY_REFRESH_TOKEN).orEmpty()
        val recoveryError = arguments?.getString(ARG_RECOVERY_ERROR)
        if (arguments?.containsKey(ARG_RECOVERY_TOKEN) == true || recoveryError != null) {
            mode = Mode.RESET
        }

        fun setBusy(busy: Boolean) {
            action.isEnabled = !busy && !(mode == Mode.RESET && (recoveryToken.isBlank() || recoveryRefreshToken.isBlank()))
            progress.visibility = if (busy) View.VISIBLE else View.GONE
        }

        fun render(message: String = "") {
            name.visibility = if (mode == Mode.SIGNUP) View.VISIBLE else View.GONE
            phone.visibility = if (mode == Mode.SIGNUP) View.VISIBLE else View.GONE
            email.visibility = if (mode == Mode.LOGIN || mode == Mode.SIGNUP || mode == Mode.FORGOT) View.VISIBLE else View.GONE
            password.visibility = if (mode != Mode.FORGOT) View.VISIBLE else View.GONE
            confirmPassword.visibility = if (mode == Mode.SIGNUP || mode == Mode.RESET) View.VISIBLE else View.GONE
            forgot.visibility = if (mode == Mode.LOGIN) View.VISIBLE else View.GONE
            toggle.visibility = if (mode == Mode.LOGIN || mode == Mode.SIGNUP) View.VISIBLE else View.GONE
            backToLogin.visibility = if (mode == Mode.FORGOT || mode == Mode.RESET) View.VISIBLE else View.GONE
            browseAsGuest.visibility = if (mode == Mode.LOGIN || mode == Mode.SIGNUP) View.VISIBLE else View.GONE
            browseAsGuest.text = when {
                continuingCheckout -> getString(R.string.auth_1)
                continuingMerchant -> getString(R.string.auth_2)
                else -> getString(R.string.auth_3)
            }

            when (mode) {
                Mode.LOGIN -> {
                    title.text = getString(R.string.auth_4)
                    subtitle.text = when {
                        continuingCheckout -> getString(R.string.auth_5)
                        continuingMerchant -> getString(R.string.auth_6)
                        else -> getString(R.string.auth_7)
                    }
                    action.text = getString(R.string.auth_8)
                    toggle.text = getString(R.string.auth_9)
                }
                Mode.SIGNUP -> {
                    title.text = getString(R.string.auth_10)
                    subtitle.text = when {
                        continuingCheckout -> getString(R.string.auth_11)
                        continuingMerchant -> getString(R.string.auth_12)
                        else -> getString(R.string.auth_13)
                    }
                    action.text = getString(R.string.auth_14)
                    toggle.text = getString(R.string.auth_15)
                }
                Mode.FORGOT -> {
                    title.text = getString(R.string.auth_16)
                    subtitle.text = getString(R.string.auth_17)
                    action.text = getString(R.string.auth_18)
                }
                Mode.RESET -> {
                    title.text = getString(R.string.auth_19)
                    subtitle.text = getString(R.string.auth_20)
                    action.text = getString(R.string.auth_21)
                }
            }

            status.text = message
            action.isEnabled = !(mode == Mode.RESET && (recoveryToken.isBlank() || recoveryRefreshToken.isBlank()))
        }

        fun goLogin(message: String = "") {
            mode = Mode.LOGIN
            password.text.clear()
            confirmPassword.text.clear()
            render(message)
        }

        toggle.setOnClickListener {
            status.text = ""
            mode = if (mode == Mode.LOGIN) Mode.SIGNUP else Mode.LOGIN
            password.text.clear()
            confirmPassword.text.clear()
            render()
        }

        forgot.setOnClickListener {
            mode = Mode.FORGOT
            password.text.clear()
            status.text = ""
            render()
        }

        backToLogin.setOnClickListener { goLogin() }
        browseAsGuest.setOnClickListener {
            when {
                continuingCheckout -> parentFragmentManager.popBackStack()
                continuingMerchant -> (activity as? MainActivity)?.showApp()
                else -> (activity as? MainActivity)?.showApp()
            }
        }

        action.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                setBusy(true)
                runCatchingCancellable {
                    when (mode) {
                        Mode.LOGIN -> {
                            repository.login(email.text.toString(), password.text.toString())
                            "LOGIN_OK"
                        }
                        Mode.SIGNUP -> {
                            val signupEmail = email.text.toString()
                            val signupPassword = password.text.toString()
                            val hasSession = repository.signup(
                                email = signupEmail,
                                password = signupPassword,
                                confirmPassword = confirmPassword.text.toString(),
                                name = name.text.toString(),
                                phone = phone.text.toString()
                            )
                            if (!hasSession) {
                                delay(250)
                                repository.login(signupEmail, signupPassword)
                            }
                            "SIGNUP_OK"
                        }
                        Mode.FORGOT -> {
                            repository.requestPasswordReset(email.text.toString())
                            "RECOVERY_SENT"
                        }
                        Mode.RESET -> {
                            repository.resetPassword(
                                recoveryToken = recoveryToken,
                                recoveryRefreshToken = recoveryRefreshToken,
                                password = password.text.toString(),
                                confirmPassword = confirmPassword.text.toString()
                            )
                            "RESET_OK"
                        }
                    }
                }.onSuccess { result ->
                    when (result) {
                        "LOGIN_OK", "SIGNUP_OK" -> continueAfterAuthentication(afterAuth)
                        "RESET_OK" -> (activity as? MainActivity)?.showApp()
                        "RECOVERY_SENT" -> {
                            status.text = getString(R.string.auth_22)
                        }
                    }
                }.onFailure {
                    status.text = friendlyError(it)
                }
                setBusy(false)
            }
        }

        render(
            when {
                recoveryError != null -> getString(R.string.auth_23, recoveryError)
                mode == Mode.RESET && (recoveryToken.isBlank() || recoveryRefreshToken.isBlank()) -> getString(R.string.auth_24)
                else -> arguments?.getString(ARG_NOTICE).orEmpty()
            }
        )
    }

    private fun continueAfterAuthentication(destination: String?) {
        val host = activity as? MainActivity ?: return
        when {
            destination == AFTER_AUTH_CHECKOUT && Cart.all().isNotEmpty() -> {
                parentFragmentManager.popBackStackImmediate()
                host.show(CheckoutFragment())
            }
            destination == AFTER_AUTH_MERCHANT -> {
                parentFragmentManager.popBackStackImmediate()
                host.openMerchantPortal()
            }
            else -> host.showApp()
        }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("Invalid login credentials", true) -> getString(R.string.auth_25)
            raw.contains("Email not confirmed", true) -> getString(R.string.auth_26)
            raw.contains("User already registered", true) -> getString(R.string.auth_27)
            raw.contains("Password should be", true) -> getString(R.string.auth_28)
            raw.contains("rate limit", true) -> getString(R.string.auth_29)
            else -> CustomerErrorMessages.from(error, getString(R.string.auth_30))
        }
    }
}
