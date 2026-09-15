package com.tani.app.ui.monetization

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.MonetizationRepository
import com.tani.app.ui.seller.MerchantUi
import kotlinx.coroutines.launch

class MonetizationFragment : Fragment() {
    private val repository = MonetizationRepository()
    private lateinit var root: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MerchantUi.dp(context, 16), MerchantUi.dp(context, 18), MerchantUi.dp(context, 16), MerchantUi.dp(context, 28))
            MerchantUi.decorateRoot(this)
        }
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) { load() }

    private fun load() {
        val sellerId = requireArguments().getString(ARG_SELLER) ?: return
        val context = requireContext()
        root.removeAllViews()
        root.addView(MerchantUi.title(context, "النمو والظهور"))
        root.addView(MerchantUi.subtitle(context, "كل الخيارات المدفوعة منفصلة وواضحة، وأي ظهور ممول يُعرض للعميل بوسم «ممول»."))
        root.addView(MerchantUi.card(context, soft = true).apply {
            addView(MerchantUi.text(context, "جاري تحميل خيارات النمو…", 14f, true))
        })

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                Triple(repository.plans(), repository.mySubscriptions(), repository.featuredRequests(sellerId))
            }.onSuccess { (plans, subscriptions, featured) ->
                root.removeAllViews()
                root.addView(MerchantUi.title(context, "النمو والظهور"))
                root.addView(MerchantUi.subtitle(context, "اختاري فقط ما يخدم متجرك؛ الاشتراك والظهور الممول لا يغيّران المحتوى العضوي بدون وسم."))

                root.addView(MerchantUi.sectionTitle(context, "الاشتراك"))
                if (plans.isEmpty()) {
                    root.addView(MerchantUi.emptyCard(context, "لا توجد خطط مفعلة حالياً", "ستظهر الخطط هنا عندما تعتمدها إدارة تاني."))
                } else {
                    plans.forEach { plan ->
                        root.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, plan.name, 17f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(MerchantUi.pill(context, "${money(plan.price)} جنيه", MerchantUi.Tone.BRAND))
                            })
                            plan.description.takeIf { it.isNotBlank() }?.let {
                                addView(MerchantUi.muted(context, it, 12.5f).apply { setPadding(0, MerchantUi.dp(context, 6), 0, 0) })
                            }
                            addView(MerchantUi.muted(context, "المدة: ${plan.duration_days} يوم", 12f).apply {
                                setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 8))
                            })
                            addView(MerchantUi.primaryButton(context, "طلب الاشتراك") {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    runCatching { repository.requestSubscription(sellerId, plan.id, "") }
                                        .onSuccess { Toast.makeText(context, "تم إرسال طلب الاشتراك", Toast.LENGTH_SHORT).show(); load() }
                                        .onFailure { Toast.makeText(context, it.message ?: "تعذر إرسال الطلب", Toast.LENGTH_LONG).show() }
                                }
                            })
                        })
                    }
                }

                root.addView(MerchantUi.sectionTitle(context, "اشتراكاتي"))
                if (subscriptions.isEmpty()) {
                    root.addView(MerchantUi.emptyCard(context, "لا يوجد اشتراك حالياً", "يمكنك تشغيل المتجر وإدارة الطلبات حتى بدون اشتراك نشط."))
                } else {
                    subscriptions.take(10).forEach { item ->
                        root.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, "اشتراك المتجر", 15f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(MerchantUi.pill(context, statusLabel(item.status), statusTone(item.status)))
                            })
                            addView(MerchantUi.muted(context, "${dateLabel(item.starts_at)} → ${item.ends_at?.let(::dateLabel) ?: "بدون تاريخ نهاية"}", 12f).apply {
                                setPadding(0, MerchantUi.dp(context, 6), 0, 0)
                            })
                        })
                    }
                }

                root.addView(MerchantUi.sectionTitle(context, "الظهور المميز"))
                root.addView(MerchantUi.card(context, soft = true).apply {
                    addView(MerchantUi.text(context, "ظهور ممول في الصفحة الرئيسية", 16f, true))
                    addView(MerchantUi.muted(context, "يظهر للعميل بوسم واضح «ممول» بعد موافقة الإدارة.", 12.5f).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 9))
                    })
                    addView(MerchantUi.primaryButton(context, "طلب ظهور ممول") {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { repository.requestFeatured(sellerId = sellerId, productId = null, placement = "home", note = "طلب ظهور مميز") }
                                .onSuccess { Toast.makeText(context, "تم إرسال الطلب للمراجعة", Toast.LENGTH_LONG).show(); load() }
                                .onFailure { Toast.makeText(context, it.message ?: "تعذر إرسال الطلب", Toast.LENGTH_LONG).show() }
                        }
                    })
                })

                if (featured.isNotEmpty()) {
                    root.addView(MerchantUi.text(context, "طلبات الظهور السابقة", 14.5f, true).apply { setPadding(0, 0, 0, MerchantUi.dp(context, 7)) })
                    featured.take(10).forEach { item ->
                        root.addView(MerchantUi.card(context).apply {
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutDirection = View.LAYOUT_DIRECTION_RTL
                                addView(MerchantUi.text(context, placementLabel(item.placement), 14f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                                addView(MerchantUi.pill(context, statusLabel(item.status), statusTone(item.status)))
                            })
                            item.created_at?.let { addView(MerchantUi.muted(context, dateLabel(it), 11.5f).apply { setPadding(0, MerchantUi.dp(context, 5), 0, 0) }) }
                        })
                    }
                }

                root.addView(MerchantUi.sectionTitle(context, "الحملات الإعلانية"))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, "حملة للمتجر", 16f, true))
                    addView(MerchantUi.muted(context, "يتم إنشاء الطلب بحالة انتظار حتى تراجعه الإدارة.", 12.5f).apply {
                        setPadding(0, MerchantUi.dp(context, 5), 0, MerchantUi.dp(context, 9))
                    })
                    addView(MerchantUi.secondaryButton(context, "إنشاء حملة للمراجعة") {
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { repository.createAdCampaign(sellerId = sellerId, name = "حملة المتجر", placement = "home", productId = null, budget = null) }
                                .onSuccess { Toast.makeText(context, "تم إنشاء الحملة وإرسالها للمراجعة", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "تعذر إنشاء الحملة", Toast.LENGTH_LONG).show() }
                        }
                    })
                })
            }.onFailure {
                root.removeAllViews()
                root.addView(MerchantUi.title(context, "النمو والظهور"))
                root.addView(MerchantUi.card(context).apply {
                    addView(MerchantUi.text(context, "تعذر تحميل خيارات النمو", 16f, true))
                    addView(MerchantUi.muted(context, it.message ?: "حاولي مرة أخرى لاحقاً").apply { setPadding(0, MerchantUi.dp(context, 5), 0, 0) })
                })
            }
        }
    }

    private fun statusLabel(status: String): String = when (status.lowercase()) {
        "pending" -> "قيد المراجعة"
        "active", "approved" -> "نشط"
        "rejected" -> "مرفوض"
        "cancelled", "canceled" -> "ملغي"
        "expired" -> "منتهي"
        else -> status
    }

    private fun statusTone(status: String): MerchantUi.Tone = when (status.lowercase()) {
        "active", "approved" -> MerchantUi.Tone.SUCCESS
        "pending" -> MerchantUi.Tone.WARNING
        "rejected", "cancelled", "canceled", "expired" -> MerchantUi.Tone.ERROR
        else -> MerchantUi.Tone.NEUTRAL
    }

    private fun placementLabel(placement: String): String = when (placement.lowercase()) {
        "home" -> "الصفحة الرئيسية"
        "category" -> "صفحة الفئة"
        "search" -> "نتائج البحث"
        else -> placement
    }

    private fun dateLabel(value: String): String = value.take(10)
    private fun money(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

    companion object {
        private const val ARG_SELLER = "seller_id"
        fun newInstance(id: String) = MonetizationFragment().apply { arguments = Bundle().apply { putString(ARG_SELLER, id) } }
    }
}
