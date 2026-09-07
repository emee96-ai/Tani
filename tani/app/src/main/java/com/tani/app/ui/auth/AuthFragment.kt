package com.tani.app.ui.auth

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
import com.tani.app.data.Repository
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
        val status = view.findViewById<TextView>(R.id.status)
        val progress = view.findViewById<ProgressBar>(R.id.progress)

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

            when (mode) {
                Mode.LOGIN -> {
                    title.text = "مرحباً بعودتك"
                    subtitle.text = "سجّلي الدخول لمتابعة طلباتك وحسابك في تاني"
                    action.text = "تسجيل الدخول"
                    toggle.text = "ليس لديك حساب؟ إنشاء حساب"
                }
                Mode.SIGNUP -> {
                    title.text = "إنشاء حساب"
                    subtitle.text = "أدخلي بياناتك الأساسية للبدء في تاني"
                    action.text = "إنشاء الحساب"
                    toggle.text = "لديك حساب؟ تسجيل الدخول"
                }
                Mode.FORGOT -> {
                    title.text = "نسيت كلمة المرور؟"
                    subtitle.text = "أدخلي بريدك وسنرسل لك رابطاً آمناً لتعيين كلمة مرور جديدة"
                    action.text = "إرسال رابط الاستعادة"
                }
                Mode.RESET -> {
                    title.text = "كلمة مرور جديدة"
                    subtitle.text = "اختاري كلمة مرور جديدة لحسابك"
                    action.text = "حفظ كلمة المرور"
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

        action.setOnClickListener {
            lifecycleScope.launch {
                setBusy(true)
                runCatching {
                    when (mode) {
                        Mode.LOGIN -> {
                            repository.login(email.text.toString(), password.text.toString())
                            "LOGIN_OK"
                        }
                        Mode.SIGNUP -> {
                            val hasSession = repository.signup(
                                email = email.text.toString(),
                                password = password.text.toString(),
                                confirmPassword = confirmPassword.text.toString(),
                                name = name.text.toString(),
                                phone = phone.text.toString()
                            )
                            if (hasSession) "SIGNUP_OK" else "EMAIL_CONFIRM"
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
                        "LOGIN_OK", "SIGNUP_OK", "RESET_OK" -> (activity as? MainActivity)?.showApp()
                        "EMAIL_CONFIRM" -> goLogin("تم إنشاء الحساب. افتحي رسالة التأكيد في بريدك ثم سجّلي الدخول.")
                        "RECOVERY_SENT" -> {
                            status.text = "إذا كان البريد مسجلاً، ستصلك رسالة لاستعادة كلمة المرور."
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
                recoveryError != null -> "تعذر فتح رابط الاستعادة: $recoveryError"
                mode == Mode.RESET && (recoveryToken.isBlank() || recoveryRefreshToken.isBlank()) -> "رابط استعادة كلمة المرور غير صالح أو منتهي. أطلبي رابطاً جديداً."
                else -> ""
            }
        )
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("Invalid login credentials", true) -> "البريد الإلكتروني أو كلمة المرور غير صحيحة"
            raw.contains("Email not confirmed", true) -> "يجب تأكيد البريد الإلكتروني أولاً"
            raw.contains("User already registered", true) -> "يوجد حساب مسجل بهذا البريد بالفعل"
            raw.contains("Password should be", true) -> "كلمة المرور لا تحقق متطلبات الأمان"
            raw.contains("rate limit", true) -> "تمت محاولات كثيرة. حاولي مرة أخرى لاحقاً"
            raw.isBlank() -> "حدث خطأ غير متوقع"
            else -> raw
        }
    }
}
