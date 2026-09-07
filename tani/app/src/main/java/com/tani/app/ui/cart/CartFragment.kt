package com.tani.app.ui.cart

import android.os.Bundle
import android.view.View
import android.widget.Button
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
        lifecycleScope.launch {
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
        val items = Cart.all()
        if (items.isEmpty()) {
            box.addView(TextView(requireContext()).apply {
                text = "السلة فارغة 🛍️\nأضيفي منتجات من المتاجر ثم ارجعي هنا."
                textSize = 17f
                setPadding(10, 30, 10, 30)
                setTextColor(requireContext().getColor(R.color.text_muted))
            })
            return
        }

        box.addView(TextView(requireContext()).apply {
            text = "${Cart.itemCount()} قطعة من ${Cart.groupedBySeller().size} متجر"
            textSize = 15f
            setTextColor(requireContext().getColor(R.color.text_muted))
            setPadding(0, 0, 0, 12)
        })

        Cart.groupedBySeller().values.forEach { group ->
            box.addView(merchantGroup(group), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 })
        }

        val summary = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            background = requireContext().getDrawable(R.drawable.bg_card)
        }
        summary.addView(summaryLine("قيمة المنتجات", MarketplaceUi.formatPrice(Cart.subtotal())))
        summary.addView(summaryLine("إجمالي التوصيل", MarketplaceUi.formatPrice(Cart.deliveryTotal())))
        summary.addView(TextView(requireContext()).apply {
            text = "الإجمالي: ${MarketplaceUi.formatPrice(Cart.grandTotal())}"
            textSize = 21f
            setTextColor(requireContext().getColor(R.color.tani_primary))
            setPadding(0, 12, 0, 6)
        })
        summary.addView(TextView(requireContext()).apply {
            text = "يتم التحقق من السعر والمخزون ورسوم التوصيل مرة أخرى عند تأكيد الطلب."
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.text_muted))
        })
        box.addView(summary)

        box.addView(Button(requireContext()).apply {
            text = "متابعة لإتمام الطلب"
            setOnClickListener {
                lifecycleScope.launch { Analytics.track("checkout_started", screen = "cart") }
                (activity as? MainActivity)?.show(CheckoutFragment())
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14; bottomMargin = 28 })
    }

    private fun merchantGroup(items: List<CartItem>): LinearLayout {
        val first = items.first()
        val storeName = first.product.store_name ?: "المتجر"
        val subtotal = items.sumOf { it.product.price * it.quantity }
        val delivery = first.product.delivery_fee

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            background = requireContext().getDrawable(R.drawable.bg_card)

            addView(TextView(requireContext()).apply {
                text = storeName
                textSize = 19f
                setTextColor(requireContext().getColor(R.color.text_dark))
            })
            items.forEach { addView(itemRow(it)) }
            addView(TextView(requireContext()).apply {
                text = "المجموع: ${MarketplaceUi.formatPrice(subtotal)}\nالتوصيل: ${MarketplaceUi.formatPrice(delivery)}"
                textSize = 14f
                setPadding(0, 10, 0, 0)
                setTextColor(requireContext().getColor(R.color.text_muted))
            })
        }
    }

    private fun itemRow(item: CartItem): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 8)
        }
        row.addView(TextView(requireContext()).apply {
            text = "${item.product.name}\n${MarketplaceUi.formatPrice(item.product.price)} × ${item.quantity} = ${MarketplaceUi.formatPrice(item.product.price * item.quantity)}"
            textSize = 15f
        })
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(requireContext()).apply {
            text = "+"
            setOnClickListener {
                if (!Cart.increase(item.product.id)) {
                    Toast.makeText(requireContext(), "وصلتِ للكمية المتاحة", Toast.LENGTH_SHORT).show()
                }
                render(); syncSilent()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(requireContext()).apply {
            text = "−"
            setOnClickListener { Cart.decrease(item.product.id); render(); syncSilent() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(requireContext()).apply {
            text = "حذف"
            setOnClickListener { Cart.remove(item.product.id); render(); syncSilent() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(actions)
        return row
    }

    private fun summaryLine(label: String, value: String) = TextView(requireContext()).apply {
        text = "$label: $value"
        textSize = 15f
        setPadding(0, 4, 0, 4)
    }

    private fun syncSilent() {
        lifecycleScope.launch { runCatching { repository.syncCart(Cart.all()) } }
    }
}
