package com.tani.app.data

import android.content.Context
import com.tani.app.security.SecureTokenStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID

/** An ambiguous HTTP outcome keeps its key across process death and sign-out. */
object PendingCheckoutStore {
    @Serializable
    data class Attempt(val key: String, val groupId: String? = null)
    private lateinit var storage: SecureTokenStorage

    fun init(context: Context) {
        storage = SecureTokenStorage(context.applicationContext, "tani_pending_checkout")
    }

    @Synchronized
    fun pending(userId: String): Attempt? {
        val name = name(userId)
        if (!storage.contains(name)) return null
        val raw = storage.getString(name)
            ?: error("تعذر قراءة محاولة الطلب المحفوظة. تواصلي مع الدعم قبل إعادة الطلب")
        return Supabase.json.decodeFromString<Attempt>(raw).also {
            UUID.fromString(it.key)
            it.groupId?.let(UUID::fromString)
        }
    }

    @Synchronized
    fun begin(userId: String): Attempt = pending(userId) ?: Attempt(UUID.randomUUID().toString()).also {
        save(userId, it)
    }

    @Synchronized
    fun resolve(userId: String, key: String, groupId: String) {
        UUID.fromString(groupId)
        val current = pending(userId) ?: error("محاولة الطلب غير موجودة")
        check(current.key == key && (current.groupId == null || current.groupId == groupId))
        save(userId, current.copy(groupId = groupId))
    }

    @Synchronized
    fun finish(userId: String, groupId: String) {
        if (pending(userId)?.groupId == groupId) {
            check(storage.putStringSync(name(userId), null)) { "تعذر حفظ تأكيد الطلب" }
        }
    }

    private fun save(userId: String, value: Attempt) {
        check(storage.putStringSync(name(userId), Supabase.json.encodeToString(value))) {
            "تعذر حفظ محاولة الطلب. لم يُرسل طلب جديد"
        }
    }

    private fun name(userId: String) = "checkout_${UUID.fromString(userId)}"
}
