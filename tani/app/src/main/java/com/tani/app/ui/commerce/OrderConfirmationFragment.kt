package com.tani.app.ui.commerce

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tani.app.MainActivity
import com.tani.app.R
import com.tani.app.data.Repository
import com.tani.app.ui.marketplace.MarketplaceUi
import kotlinx.coroutines.launch

class OrderConfirmationFragment : Fragment(R.layout.fragment_order_confirmation) {
    private val repository = Repository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val groupId = requireArguments().getString(ARG_GROUP_ID) ?: return
        val message = view.findViewById<TextView>(R.id.confirmation_message)
        val progress = view.findViewById<ProgressBar>(R.id.confirmation_progress)

        view.findViewById<Button>(R.id.confirmation_details).setOnClickListener {
            (activity as? MainActivity)?.show(OrderDetailsFragment.newInstance(groupId))
        }
        view.findViewById<Button>(R.id.confirmation_orders).setOnClickListener {
            (activity as? MainActivity)?.nav?.selectedItemId = R.id.orders
        }

        lifecycleScope.launch {
            runCatching { repository.orderGroupDetails(groupId) }
                .onSuccess { details ->
                    message.text = buildString {
                        append("رقم العملية: ${groupId.take(8).uppercase()}\n")
                        append("${details.orders.size} طلب للتجار\n")
                        append("الإجمالي: ${MarketplaceUi.formatPrice(details.group.grand_total)}\n")
                        append("طريقة الدفع: عند الاستلام")
                    }
                }
                .onFailure {
                    message.text = "تم إنشاء الطلب. يمكنك متابعة حالته من طلباتي."
                }
            progress.visibility = View.GONE
        }
    }

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        fun newInstance(groupId: String) = OrderConfirmationFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP_ID, groupId) }
        }
    }
}
