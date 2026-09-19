package com.tani.app.ui.cart

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Analytics
import com.tani.app.data.Cart
import com.tani.app.data.CartItem
import com.tani.app.data.Repository
import com.tani.app.data.Supabase
import com.tani.app.ui.auth.AuthFragment
import com.tani.app.ui.commerce.CheckoutFragment
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.launch

class CartFragment : Fragment(R.layout.fragment_cart) {
    private val repository = Repository()
    private lateinit var box: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        box = view.findViewById(R.id.cart_box)
        render()
        restoreOrSync()
    }

    private fun restoreOrSync() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (Cart.all().isEmpty()) {
                runCatching { repository.remoteCart() }
                    .onSuccess { remote ->
                        if (remote.isNotEmpty()) {
                            Cart.replace(remote.map { it.toCartItem() })
                            render()
                        }
                    }
            } else {
                runCatching { repository.syncCart(Cart.all()) }
            }
        }
    }

    private fun render() {
        box.removeAllViews()
        (activity as? MainActivity)?.refreshCartBadge()
        val items = Cart.all()
        if (items.isEmpty()) {
            box.addView(TextView(requireContext()).apply {
                text = "السلة فارغة 🛍️\nأضيفي منتجات من المتاجر ثم ارجعي هنا."
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(10, MarketplaceUi.dp(requireContext(), 38), 10, MarketplaceUi.dp(requireContext(), 38))
                setTextColor(requireContext().getColor(R.color.text_muted))
            })
            return
        }

        box.addView(TextView(requireContext()).apply {
            text = "${Cart.itemCount()} قطعة من ${Cart.groupedBySeller().size} متجر"
            textSize = 15f
            setTextColor(requireContext().getColor(R.color.text_muted))
            setPadding(0, 0, 0, MarketplaceUi.dp(requireContext(), 12))
        })

        Cart.groupedBySeller().values.forEach { group ->
            box.addView(
                merchantGroup(group),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = MarketplaceUi.dp(requireContext(), 14) }
            )
        }

        val summary = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                MarketplaceUi.dp(requireContext(), 16),
                MarketplaceUi.dp(requireContext(), 16),
                MarketplaceUi.dp(requireContext(), 16),
                MarketplaceUi.dp(requireContext(), 16)
            )
            background = requireContext().getDrawable(R.drawable.bg_card)
        }
        summary.addView(summaryLine("قيمة المنتجات", MarketplaceUi.formatPrice(Cart.subtotal())))
        summary.addView(summaryLine("إجمالي التوصيل", MarketplaceUi.formatPrice(Cart.deliveryTotal())))
        summary.addView(TextView(requireContext()).apply {
            text = "الإجمالي: ${MarketplaceUi.formatPrice(Cart.grandTotal())}"
            textSize = 21f
            setTextColor(requireContext().getColor(R.color.tani_primary))
            setPadding(0, MarketplaceUi.dp(requireContext(), 12), 0, MarketplaceUi.dp(requireContext(), 6))
        })
        summary.addView(TextView(requireContext()).apply {
            text = "يتم التحقق من السعر والمخزون ورسوم التوصيل مرة أخرى عند تأكيد الطلب."
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.text_muted))
        })
        box.addView(summary)

        box.addView(Button(requireContext()).apply {
            text = "إكمال الطلب"
            setOnClickListener {
                if (Supabase.hasStoredSession()) {
                    viewLifecycleOwner.lifecycleScope.launch { Analytics.track("checkout_started", screen = "cart") }
                    (activity as? MainActivity)?.show(CheckoutFragment())
                } else {
                    Toast.makeText(requireContext(), "سجّلي الدخول لإتمام الطلب", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.show(AuthFragment())
                }
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            MarketplaceUi.dp(requireContext(), 54)
        ).apply {
            topMargin = MarketplaceUi.dp(requireContext(), 14)
            bottomMargin = MarketplaceUi.dp(requireContext(), 28)
        })
    }

    private fun merchantGroup(items: List<CartItem>): LinearLayout {
        val first = items.first()
        val storeName = first.product.store_name ?: "المتجر"
        val subtotal = items.sumOf { it.unitPrice * it.quantity }
        val delivery = first.product.delivery_fee

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                MarketplaceUi.dp(requireContext(), 16),
                MarketplaceUi.dp(requireContext(), 16),
                MarketplaceUi.dp(requireContext(), 16),
                MarketplaceUi.dp(requireContext(), 16)
            )
            background = requireContext().getDrawable(R.drawable.bg_card)

            addView(TextView(requireContext()).apply {
                text = storeName
                textSize = 18f
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            items.forEach { addView(itemRow(it)) }
            addView(TextView(requireContext()).apply {
                text = "المجموع: ${MarketplaceUi.formatPrice(subtotal)}\nالتوصيل: ${MarketplaceUi.formatPrice(delivery)}"
                textSize = 14f
                setPadding(0, MarketplaceUi.dp(requireContext(), 10), 0, 0)
                setTextColor(requireContext().getColor(R.color.text_muted))
            })
        }
    }

    private fun itemRow(item: CartItem): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, MarketplaceUi.dp(requireContext(), 12), 0, MarketplaceUi.dp(requireContext(), 10))
        }

        val top = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val image = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                MarketplaceUi.dp(requireContext(), 92),
                MarketplaceUi.dp(requireContext(), 92)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_image_placeholder)
            setBackgroundColor(requireContext().getColor(R.color.brand_soft))
            contentDescription = item.product.name
        }
        repository.productImageUrl(item.product.image)?.let { MarketplaceUi.loadImage(viewLifecycleOwner.lifecycleScope, image, it) }

        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = MarketplaceUi.dp(requireContext(), 12)
            }
        }
        info.addView(TextView(requireContext()).apply {
            text = item.product.name
            textSize = 17f
            setTextColor(requireContext().getColor(R.color.text_dark))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        item.variantName?.let { variantName ->
            info.addView(TextView(requireContext()).apply {
                text = "الخيار: $variantName"
                textSize = 14f
                setPadding(0, MarketplaceUi.dp(requireContext(), 4), 0, 0)
                setTextColor(requireContext().getColor(R.color.tani_primary))
            })
        }
        info.addView(TextView(requireContext()).apply {
            text = "${MarketplaceUi.formatPrice(item.unitPrice)} × ${item.quantity}"
            textSize = 14f
            setPadding(0, MarketplaceUi.dp(requireContext(), 5), 0, 0)
            setTextColor(requireContext().getColor(R.color.text_muted))
        })
        info.addView(TextView(requireContext()).apply {
            text = "المجموع ${MarketplaceUi.formatPrice(item.unitPrice * item.quantity)}"
            textSize = 14f
            setPadding(0, MarketplaceUi.dp(requireContext(), 4), 0, 0)
            setTextColor(requireContext().getColor(R.color.tani_primary))
        })
        top.addView(image)
        top.addView(info)
        row.addView(top)

        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, MarketplaceUi.dp(requireContext(), 10), 0, 0)
        }
        actions.addView(Button(requireContext()).apply {
            text = "+"
            minWidth = 0
            setOnClickListener {
                if (!Cart.increase(item.key)) {
                    Toast.makeText(requireContext(), "وصلتِ للكمية المتاحة", Toast.LENGTH_SHORT).show()
                }
                render(); syncSilent()
            }
        }, LinearLayout.LayoutParams(MarketplaceUi.dp(requireContext(), 52), MarketplaceUi.dp(requireContext(), 48)))
        actions.addView(TextView(requireContext()).apply {
            text = item.quantity.toString()
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(requireContext().getColor(R.color.text_dark))
        }, LinearLayout.LayoutParams(MarketplaceUi.dp(requireContext(), 48), MarketplaceUi.dp(requireContext(), 48)))
        actions.addView(Button(requireContext()).apply {
            text = "−"
            minWidth = 0
            setOnClickListener { Cart.decrease(item.key); render(); syncSilent() }
        }, LinearLayout.LayoutParams(MarketplaceUi.dp(requireContext(), 52), MarketplaceUi.dp(requireContext(), 48)))
        actions.addView(TextView(requireContext()).apply {
            text = "حذف"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(MarketplaceUi.dp(requireContext(), 14), 0, MarketplaceUi.dp(requireContext(), 14), 0)
            setTextColor(requireContext().getColor(R.color.error))
            isClickable = true
            isFocusable = true
            setOnClickListener { Cart.remove(item.key); render(); syncSilent() }
        }, LinearLayout.LayoutParams(0, MarketplaceUi.dp(requireContext(), 48), 1f))
        row.addView(actions)
        return row
    }

    private fun summaryLine(label: String, value: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(0, MarketplaceUi.dp(requireContext(), 5), 0, MarketplaceUi.dp(requireContext(), 5))
        addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_muted))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(requireContext()).apply {
            text = value
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_dark))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
    }

    private fun syncSilent() {
        (activity as? MainActivity)?.refreshCartBadge()
        viewLifecycleOwner.lifecycleScope.launch { runCatching { repository.syncCart(Cart.all()) } }
    }
}
