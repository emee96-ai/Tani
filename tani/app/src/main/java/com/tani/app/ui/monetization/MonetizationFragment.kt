package com.tani.app.ui.monetization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.R
import com.tani.app.data.repository.MonetizationRepository
import com.tani.app.ui.common.ScreenUi
import kotlinx.coroutines.launch

class MonetizationFragment : Fragment() {
    private val repository = MonetizationRepository()
    private lateinit var root: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        val context = requireContext()
        root = ScreenUi.root(context)
        return ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.tani_background))
            addView(root)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        load()
    }

    private fun load() {
        val sellerId = requireArguments().getString(ARG_SELLER) ?: return
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, "النمو المدفوع"))
        root.addView(
            ScreenUi.subtitle(
                context,
                "الاشتراكات والظهور المميز والإعلانات قابلة للتهيئة من الإدارة. لا يتم افتراض أي عمولة أو سعر غير معتمد."
            )
        )

        lifecycleScope.launch {
            runCatching {
                Triple(
                    repository.plans(),
                    repository.mySubscriptions(),
                    repository.featuredRequests(sellerId)
                )
            }.onSuccess { (plans, subscriptions, featured) ->
                root.addView(ScreenUi.text(context, "الخطط", 19f, true))
                if (plans.isEmpty()) {
                    root.addView(ScreenUi.muted(context, "لا توجد خطط مدفوعة مفعلة حالياً."))
                }
                plans.forEach { plan ->
                    root.addView(
                        ScreenUi.card(context).apply {
                            addView(ScreenUi.text(context, plan.name, 17f, true))
                            addView(ScreenUi.muted(context, plan.description))
                            addView(ScreenUi.muted(context, "${plan.price} SDG • ${plan.duration_days} يوم"))
                            addView(
                                ScreenUi.button(context, "طلب الاشتراك") {
                                    lifecycleScope.launch {
                                        runCatching {
                                            repository.requestSubscription(sellerId, plan.id, "")
                                        }.onSuccess {
                                            Toast.makeText(context, "تم إرسال الطلب", Toast.LENGTH_SHORT).show()
                                            load()
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "تعذر إرسال الطلب",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    )
                }

                root.addView(ScreenUi.text(context, "اشتراكاتي", 19f, true))
                if (subscriptions.isEmpty()) {
                    root.addView(ScreenUi.muted(context, "لا يوجد اشتراك نشط."))
                }
                subscriptions.take(10).forEach { item ->
                    root.addView(
                        ScreenUi.muted(
                            context,
                            "${item.status} • ${item.starts_at} → ${item.ends_at ?: "بدون تاريخ نهاية"}"
                        )
                    )
                }

                root.addView(ScreenUi.text(context, "الظهور المميز", 19f, true))
                root.addView(
                    ScreenUi.button(context, "طلب ظهور ممول في الصفحة الرئيسية") {
                        lifecycleScope.launch {
                            runCatching {
                                repository.requestFeatured(
                                    sellerId = sellerId,
                                    productId = null,
                                    placement = "home",
                                    note = "طلب ظهور مميز"
                                )
                            }.onSuccess {
                                Toast.makeText(
                                    context,
                                    "تم إرسال الطلب وسيظهر للمستخدمين بوسم ممول بعد الاعتماد",
                                    Toast.LENGTH_LONG
                                ).show()
                                load()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "تعذر إرسال الطلب",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
                featured.take(10).forEach { item ->
                    root.addView(
                        ScreenUi.muted(
                            context,
                            "${item.placement} • ${item.status} • ${item.created_at ?: ""}"
                        )
                    )
                }

                root.addView(ScreenUi.text(context, "الإعلانات", 19f, true))
                root.addView(
                    ScreenUi.button(context, "إنشاء حملة للمراجعة") {
                        lifecycleScope.launch {
                            runCatching {
                                repository.createAdCampaign(
                                    sellerId = sellerId,
                                    name = "حملة المتجر",
                                    placement = "home",
                                    productId = null,
                                    budget = null
                                )
                            }.onSuccess {
                                Toast.makeText(
                                    context,
                                    "تم إنشاء الحملة بحالة انتظار",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "تعذر إنشاء الحملة",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }.onFailure {
                root.addView(ScreenUi.muted(context, it.message ?: "تعذر تحميل أدوات النمو"))
            }
        }
    }

    companion object {
        private const val ARG_SELLER = "seller_id"
        fun newInstance(id: String) = MonetizationFragment().apply {
            arguments = Bundle().apply { putString(ARG_SELLER, id) }
        }
    }
}
