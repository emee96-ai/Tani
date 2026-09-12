package com.tani.app.domain.orders

object OrderStateMachine {
    private val allowedTransitions: Map<String, Set<String>> = mapOf(
        "pending" to setOf("accepted", "rejected", "cancelled"),
        "accepted" to setOf("preparing", "cancelled"),
        "preparing" to setOf("ready", "cancelled"),
        "ready" to setOf("out_for_delivery", "cancelled"),
        "out_for_delivery" to setOf("delivered", "failed")
    )

    fun canTransition(from: String, to: String): Boolean =
        allowedTransitions[from]?.contains(to) == true

    fun isFinal(status: String): Boolean =
        status in setOf("delivered", "cancelled", "rejected", "failed")
}
