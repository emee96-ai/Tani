package com.tani.app.ui.trust

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.TrustRepository
import com.tani.app.ui.common.ScreenUi
import kotlinx.coroutines.launch

class SupportCenterFragment : Fragment() {
    private val repository = TrustRepository()
    private lateinit var root: LinearLayout
    private var linkedOrderId: String? = null
    private var linkedSellerId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        root = ScreenUi.root(context)
        return ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        linkedOrderId = arguments?.getString(ARG_ORDER_ID)
        linkedSellerId = arguments?.getString(ARG_SELLER_ID)
        if (linkedOrderId.isNullOrBlank()) renderHome() else renderComplaintForm()
    }

    private fun renderHome() {
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, "الدعم والثقة"))
        root.addView(
            ScreenUi.subtitle(
                context,
                "تذاكر الدعم والشكاوى محفوظة داخل حسابك ويمكن لفريق تاني متابعتها وربطها بسجل الطلب عند الحاجة."
            )
        )
        root.addView(ScreenUi.button(context, "فتح تذكرة دعم") { renderTicketForm() })
        root.addView(ScreenUi.button(context, "إرسال شكوى") { renderComplaintForm() })
        root.addView(ScreenUi.button(context, "تحديث") { renderHome() })
        loadExisting()
    }

    private fun loadExisting() {
        lifecycleScope.launch {
            runCatching { repository.tickets() to repository.complaints() }
                .onSuccess { (tickets, complaints) ->
                    val context = requireContext()
                    root.addView(ScreenUi.text(context, "تذاكر الدعم", 19f, true))
                    if (tickets.isEmpty()) root.addView(ScreenUi.muted(context, "لا توجد تذاكر دعم حتى الآن."))
                    tickets.take(15).forEach { ticket ->
                        root.addView(ScreenUi.card(context).apply {
                            addView(ScreenUi.text(context, ticket.subject, 16f, true))
                            addView(ScreenUi.muted(context, "${status(ticket.status)} • ${ticket.priority} • ${ticket.created_at ?: ""}"))
                            addView(ScreenUi.text(context, ticket.description.take(300), 14f))
                        })
                    }
                    root.addView(ScreenUi.spacer(context, 10))
                    root.addView(ScreenUi.text(context, "الشكاوى", 19f, true))
                    if (complaints.isEmpty()) root.addView(ScreenUi.muted(context, "لا توجد شكاوى حتى الآن."))
                    complaints.take(15).forEach { item ->
                        root.addView(ScreenUi.card(context).apply {
                            addView(ScreenUi.text(context, item.subject, 16f, true))
                            addView(ScreenUi.muted(context, "${status(item.status)} • ${categoryLabel(item.category)}"))
                            addView(ScreenUi.text(context, item.description.take(300), 14f))
                            item.resolution?.takeIf { it.isNotBlank() }?.let { addView(ScreenUi.muted(context, "الحل: $it")) }
                        })
                    }
                }
                .onFailure { toast(it.message ?: "تعذر تحميل الدعم") }
        }
    }

    private fun renderTicketForm() {
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, "تذكرة دعم جديدة"))
        val subject = ScreenUi.input(context, "عنوان المشكلة")
        val body = ScreenUi.input(context, "اكتبي التفاصيل", true)
        root.addView(subject)
        root.addView(body)
        root.addView(ScreenUi.button(context, "إرسال") {
            lifecycleScope.launch {
                runCatching { repository.createTicket(subject.text.toString(), body.text.toString()) }
                    .onSuccess { toast("تم إنشاء التذكرة"); renderHome() }
                    .onFailure { toast(it.message ?: "تعذر إنشاء التذكرة") }
            }
        })
        root.addView(ScreenUi.button(context, "رجوع") { renderHome() })
    }

    private fun renderComplaintForm() {
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, "شكوى جديدة"))
        val orderId = linkedOrderId
        val sellerId = linkedSellerId
        if (!orderId.isNullOrBlank()) root.addView(ScreenUi.subtitle(context, "هذه الشكوى مرتبطة بالطلب ${orderId.take(8).uppercase()}."))

        val categories = listOf(
            "order" to "مشكلة في الطلب",
            "availability" to "المنتج غير متوفر",
            "merchant_response" to "التاجر لم يرد",
            "delivery_delay" to "تأخر التوصيل",
            "product" to "مشكلة في المنتج",
            "fees" to "مشكلة في الرسوم",
            "cancellation" to "إلغاء",
            "dispute" to "نزاع",
            "other" to "أخرى"
        )
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories.map { it.second })
        }
        val subject = ScreenUi.input(context, "عنوان الشكوى")
        val body = ScreenUi.input(context, "التفاصيل", true)
        root.addView(spinner)
        root.addView(ScreenUi.spacer(context))
        root.addView(subject)
        root.addView(body)
        root.addView(ScreenUi.button(context, "إرسال الشكوى") {
            lifecycleScope.launch {
                val category = categories[spinner.selectedItemPosition.coerceIn(categories.indices)]
                runCatching {
                    repository.createComplaint(
                        subject.text.toString(),
                        body.text.toString(),
                        category.first,
                        orderId,
                        sellerId
                    )
                }.onSuccess { toast("تم إرسال الشكوى"); linkedOrderId = null; linkedSellerId = null; renderHome() }
                    .onFailure { toast(it.message ?: "تعذر إرسال الشكوى") }
            }
        })
        root.addView(ScreenUi.button(context, "رجوع") { linkedOrderId = null; linkedSellerId = null; renderHome() })
    }

    private fun status(value: String) = when (value) {
        "open" -> "مفتوحة"
        "in_progress" -> "قيد المعالجة"
        "closed" -> "مغلقة"
        "resolved" -> "تم الحل"
        else -> value
    }

    private fun categoryLabel(value: String) = when (value) {
        "order" -> "طلب"
        "availability" -> "توفر"
        "merchant_response" -> "استجابة التاجر"
        "delivery_delay" -> "توصيل"
        "product" -> "منتج"
        "fees" -> "رسوم"
        "cancellation" -> "إلغاء"
        "dispute" -> "نزاع"
        else -> "أخرى"
    }

    private fun toast(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show()

    companion object {
        private const val ARG_ORDER_ID = "order_id"
        private const val ARG_SELLER_ID = "seller_id"
        fun newComplaint(orderId: String, sellerId: String?) = SupportCenterFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ORDER_ID, orderId)
                sellerId?.let { putString(ARG_SELLER_ID, it) }
            }
        }
    }
}
