package com.tani.app.ui.profile

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
import com.tani.app.data.AccountProfile
import com.tani.app.data.cache.AppContentStore
import com.tani.app.ui.commerce.AddressesFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL

class AccountFragment : Fragment(R.layout.fragment_account) {

    private val repository = Repository()

    private lateinit var progress: ProgressBar
    private lateinit var content: LinearLayout
    private lateinit var avatar: ImageView
    private lateinit var editButton: Button
    private lateinit var changeAvatarButton: Button
    private lateinit var saveButton: Button
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var role: TextView

    private var editMode = false
    private var selectedAvatarBytes: ByteArray? = null
    private var currentAvatarUrl: String? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) prepareAvatar(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress = view.findViewById(R.id.account_progress)
        content = view.findViewById(R.id.account_content)
        avatar = view.findViewById(R.id.account_avatar)
        editButton = view.findViewById(R.id.account_edit)
        changeAvatarButton = view.findViewById(R.id.account_change_avatar)
        saveButton = view.findViewById(R.id.account_save)
        name = view.findViewById(R.id.account_name)
        email = view.findViewById(R.id.account_email)
        phone = view.findViewById(R.id.account_phone)
        role = view.findViewById(R.id.account_role)

        editButton.setOnClickListener {
            if (editMode) {
                selectedAvatarBytes = null
                setEditMode(false)
                loadProfile()
            } else {
                setEditMode(true)
            }
        }
        changeAvatarButton.setOnClickListener { pickAvatar.launch("image/*") }
        saveButton.setOnClickListener { saveProfile() }
        view.findViewById<Button>(R.id.account_addresses).setOnClickListener {
            (activity as? MainActivity)?.show(AddressesFragment())
        }

        setEditMode(false)
        loadProfile()
    }

    private fun loadProfile() {
        AppContentStore.account?.let {
            renderAccount(it)
            setLoading(false)
            return
        }
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.accountProfile() }
                .onSuccess { account ->
                    AppContentStore.updateAccount(account)
                    renderAccount(account)
                }
                .onFailure {
                    Toast.makeText(
                        requireContext(),
                        it.message ?: "تعذر تحميل بيانات الحساب",
                        Toast.LENGTH_LONG
                    ).show()
                }
            setLoading(false)
        }
    }

    private fun renderAccount(account: AccountProfile) {
        currentAvatarUrl = account.profile.avatar_url
        name.setText(account.profile.name)
        email.setText(account.email)
        phone.setText(account.profile.phone)
        role.text = when (account.profile.role) {
            "admin" -> "نوع الحساب: إدارة"
            "seller" -> "نوع الحساب: تاجر"
            else -> "نوع الحساب: عميل"
        }
        val avatarUrl = account.profile.avatar_url
        if (!avatarUrl.isNullOrBlank()) loadRemoteAvatar(avatarUrl) else renderDefaultAvatar()
    }

    private fun saveProfile() {
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val uploadedAvatar = selectedAvatarBytes?.let { repository.uploadAvatar(it) }
                repository.updateProfile(
                    name = name.text.toString(),
                    phone = phone.text.toString(),
                    avatarUrl = uploadedAvatar
                )
            }.onSuccess { updated ->
                AppContentStore.updateAccount(AccountProfile(updated, email.text.toString()))
                currentAvatarUrl = updated.avatar_url
                selectedAvatarBytes = null
                name.setText(updated.name)
                phone.setText(updated.phone)
                setEditMode(false)
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

    private fun setEditMode(enabled: Boolean) {
        editMode = enabled
        editButton.text = if (enabled) "إلغاء" else "تعديل"
        changeAvatarButton.visibility = if (enabled) View.VISIBLE else View.GONE
        saveButton.visibility = if (enabled) View.VISIBLE else View.GONE
        setEditable(name, enabled)
        setEditable(phone, enabled)
        email.isFocusable = false
        email.isFocusableInTouchMode = false
        email.isCursorVisible = false
        email.isClickable = false
        email.setBackgroundResource(android.R.color.transparent)
    }

    private fun setEditable(field: EditText, enabled: Boolean) {
        field.isFocusable = enabled
        field.isFocusableInTouchMode = enabled
        field.isCursorVisible = enabled
        field.isClickable = enabled
        field.setBackgroundResource(if (enabled) R.drawable.bg_field else android.R.color.transparent)
        if (enabled) {
            val horizontal = (14 * resources.displayMetrics.density).toInt()
            field.setPadding(horizontal, field.paddingTop, horizontal, field.paddingBottom)
        }
    }

    private fun prepareAvatar(uri: Uri) {
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
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

    private fun loadRemoteAvatar(url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null && isAdded) {
                avatar.setPadding(0, 0, 0, 0)
                avatar.setImageBitmap(bitmap)
            } else if (isAdded && currentAvatarUrl == url) {
                renderDefaultAvatar()
            }
        }
    }

    private fun renderDefaultAvatar() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        avatar.setPadding(pad, pad, pad, pad)
        avatar.setImageResource(android.R.drawable.ic_menu_myplaces)
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / largest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        content.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun setActionsEnabled(enabled: Boolean) {
        editButton.isEnabled = enabled
        saveButton.isEnabled = enabled
        changeAvatarButton.isEnabled = enabled
    }
}
