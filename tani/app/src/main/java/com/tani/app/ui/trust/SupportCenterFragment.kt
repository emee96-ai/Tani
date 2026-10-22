package com.tani.app.ui.trust

import com.tani.app.data.network.CustomerErrorMessages

import com.tani.app.util.runCatchingCancellable

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
import com.tani.app.data.Supabase
import com.tani.app.data.trust.SupportTicket
import com.tani.app.data.trust.TicketMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SupportCenterFragment : Fragment() {
    private val repository = TrustRepository()
    private lateinit var root: LinearLayout
    private var linkedOrderId: String? = null
    private var linkedSellerId: String? = null
    private var loadJob: Job? = null

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
        loadJob?.cancel()
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, getString(R.string.support_center_1)))
        root.addView(
            ScreenUi.subtitle(
                context,
                getString(R.string.support_center_2)
            )
        )
        root.addView(ScreenUi.button(context, getString(R.string.support_center_3)) { renderTicketForm() })
        root.addView(ScreenUi.button(context, getString(R.string.support_center_4)) { renderComplaintForm() })
        root.addView(ScreenUi.button(context, getString(R.string.support_center_5)) { renderHome() })
        loadExisting()
    }

    private fun loadExisting() {
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.tickets() to repository.complaints() }
                .onSuccess { (tickets, complaints) ->
                    val context = requireContext()
                    root.addView(ScreenUi.text(context, getString(R.string.support_center_6), 22f, true))
                    if (tickets.isEmpty()) root.addView(ScreenUi.muted(context, getString(R.string.support_center_7)))
                    tickets.take(15).forEach { ticket ->
                        root.addView(ScreenUi.card(context).apply {
                            addView(ScreenUi.text(context, ticket.subject, 16f, true))
                            addView(ScreenUi.muted(context, "${status(ticket.status)} • ${ticket.priority} • ${ticket.created_at ?: ""}"))
                            addView(ScreenUi.text(context, ticket.description.take(300), 14f))
                            addView(ScreenUi.button(context, getString(R.string.support_center_8)) { renderConversation(ticket) })
                        })
                    }
                    root.addView(ScreenUi.spacer(context, 10))
                    root.addView(ScreenUi.text(context, getString(R.string.support_center_9), 22f, true))
                    if (complaints.isEmpty()) root.addView(ScreenUi.muted(context, getString(R.string.support_center_10)))
                    complaints.take(15).forEach { item ->
                        root.addView(ScreenUi.card(context).apply {
                            addView(ScreenUi.text(context, item.subject, 16f, true))
                            addView(ScreenUi.muted(context, "${status(item.status)} • ${categoryLabel(item.category)}"))
                            addView(ScreenUi.text(context, item.description.take(300), 14f))
                            item.resolution?.takeIf { it.isNotBlank() }?.let { addView(ScreenUi.muted(context, getString(R.string.support_center_11, it))) }
                        })
                    }
                }
                .onFailure { toast(CustomerErrorMessages.from(it, getString(R.string.support_center_12))) }
        }
    }

    private fun renderTicketForm() {
        loadJob?.cancel()
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, getString(R.string.support_center_13)))
        val subject = ScreenUi.input(context, getString(R.string.support_center_14))
        val body = ScreenUi.input(context, getString(R.string.support_center_15), true)
        root.addView(subject)
        root.addView(body)
        root.addView(ScreenUi.button(context, getString(R.string.order_details_20)) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatchingCancellable { repository.createTicket(subject.text.toString(), body.text.toString()) }
                    .onSuccess { toast(getString(R.string.support_center_16)); renderHome() }
                    .onFailure { toast(CustomerErrorMessages.from(it, getString(R.string.support_center_17))) }
            }
        })
        root.addView(ScreenUi.button(context, getString(R.string.checkout_41)) { renderHome() })
    }

    private fun renderComplaintForm() {
        loadJob?.cancel()
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, getString(R.string.support_center_18)))
        val orderId = linkedOrderId
        val sellerId = linkedSellerId
        if (!orderId.isNullOrBlank()) root.addView(ScreenUi.subtitle(context, getString(R.string.support_center_19, (orderId.take(8).uppercase()))))

        val categories = listOf(
            "order" to getString(R.string.support_center_20),
            "availability" to getString(R.string.support_center_21),
            "merchant_response" to getString(R.string.support_center_22),
            "delivery_delay" to getString(R.string.support_center_23),
            "product" to getString(R.string.support_center_24),
            "fees" to getString(R.string.support_center_25),
            "cancellation" to getString(R.string.addresses_19),
            "dispute" to getString(R.string.support_center_26),
            "other" to getString(R.string.support_center_27)
        )
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories.map { it.second })
        }
        val subject = ScreenUi.input(context, getString(R.string.support_center_28))
        val body = ScreenUi.input(context, getString(R.string.support_center_29), true)
        root.addView(spinner)
        root.addView(ScreenUi.spacer(context))
        root.addView(subject)
        root.addView(body)
        root.addView(ScreenUi.button(context, getString(R.string.support_center_30)) {
            viewLifecycleOwner.lifecycleScope.launch {
                val category = categories[spinner.selectedItemPosition.coerceIn(categories.indices)]
                runCatchingCancellable {
                    repository.createComplaint(
                        subject.text.toString(),
                        body.text.toString(),
                        category.first,
                        orderId,
                        sellerId
                    )
                }.onSuccess { toast(getString(R.string.support_center_31)); linkedOrderId = null; linkedSellerId = null; renderHome() }
                    .onFailure { toast(CustomerErrorMessages.from(it, getString(R.string.support_center_32))) }
            }
        })
        root.addView(ScreenUi.button(context, getString(R.string.checkout_41)) { linkedOrderId = null; linkedSellerId = null; renderHome() })
    }

    private fun status(value: String) = when (value) {
        "open" -> getString(R.string.support_center_33)
        "in_progress" -> getString(R.string.support_center_34)
        "closed" -> getString(R.string.support_center_35)
        "resolved" -> getString(R.string.support_center_36)
        else -> value
    }

    private fun renderConversation(ticket: SupportTicket) {
        loadJob?.cancel()
        val context = requireContext()
        root.removeAllViews()
        root.addView(ScreenUi.title(context, ticket.subject))
        root.addView(ScreenUi.subtitle(context, ticket.description))
        root.addView(ScreenUi.button(context, getString(R.string.support_center_37)) { renderHome() })
        root.addView(ScreenUi.button(context, getString(R.string.support_center_38)) { renderConversation(ticket) })
        val messagesBox = ScreenUi.root(context)
        root.addView(messagesBox)
        val reply = ScreenUi.input(context, getString(R.string.support_center_39), true)
        val send = ScreenUi.button(context, getString(R.string.support_center_40)) { }
        send.isEnabled = false
        root.addView(reply)
        root.addView(send)
        val messages = mutableListOf<TicketMessage>()
        val older = ScreenUi.button(context, getString(R.string.support_center_41)) { }
        root.addView(older)
        older.visibility = View.GONE

        fun showMessages() {
            messagesBox.removeAllViews()
            if (messages.isEmpty()) messagesBox.addView(ScreenUi.muted(context, getString(R.string.support_center_42)))
            messages.distinctBy { it.id }.sortedWith(compareBy({ it.created_at }, { it.id })).forEach { item ->
                messagesBox.addView(ScreenUi.card(context).apply {
                    addView(ScreenUi.text(context, if (item.sender_id == Supabase.userId) getString(R.string.support_center_43) else getString(R.string.support_center_44), 14f, true))
                    addView(ScreenUi.text(context, item.body, 16f))
                    addView(ScreenUi.muted(context, item.created_at.orEmpty()))
                })
            }
        }
        older.setOnClickListener {
            older.isEnabled = false
            loadJob = viewLifecycleOwner.lifecycleScope.launch {
                runCatchingCancellable { repository.ticketMessages(ticket.id, messages.lastOrNull()) }
                    .onSuccess { page ->
                        messages.addAll(page)
                        showMessages()
                        older.visibility = if (page.size == 100) View.VISIBLE else View.GONE
                    }.onFailure { toast(CustomerErrorMessages.from(it, getString(R.string.support_center_45))) }
                older.isEnabled = true
            }
        }
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            runCatchingCancellable { repository.ticket(ticket.id) to repository.ticketMessages(ticket.id) }
                .onSuccess { (current, page) ->
                    messages.addAll(page)
                    showMessages()
                    older.visibility = if (page.size == 100) View.VISIBLE else View.GONE
                    val open = current.status in listOf("open", "in_progress")
                    send.isEnabled = open
                    reply.isEnabled = open
                    if (!open) root.addView(ScreenUi.muted(context, getString(R.string.support_center_46, (status(current.status)))))
                }.onFailure { toast(CustomerErrorMessages.from(it, getString(R.string.support_center_47))) }
        }
        send.setOnClickListener {
            send.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                runCatchingCancellable { repository.sendTicketMessage(ticket.id, reply.text.toString()) }
                    .onSuccess { renderConversation(ticket) }
                    .onFailure { toast(CustomerErrorMessages.from(it, getString(R.string.support_center_48))); send.isEnabled = true }
            }
        }
    }

    private fun categoryLabel(value: String) = when (value) {
        "order" -> getString(R.string.support_center_49)
        "availability" -> getString(R.string.support_center_50)
        "merchant_response" -> getString(R.string.support_center_51)
        "delivery_delay" -> getString(R.string.support_center_52)
        "product" -> getString(R.string.order_details_13)
        "fees" -> getString(R.string.support_center_53)
        "cancellation" -> getString(R.string.addresses_19)
        "dispute" -> getString(R.string.support_center_26)
        else -> getString(R.string.support_center_27)
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
