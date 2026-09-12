package com.tani.app.domain.orders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStateMachineTest {
    @Test
    fun validTransitionsAreAccepted() {
        assertTrue(OrderStateMachine.canTransition("pending", "accepted"))
        assertTrue(OrderStateMachine.canTransition("accepted", "preparing"))
        assertTrue(OrderStateMachine.canTransition("preparing", "ready"))
        assertTrue(OrderStateMachine.canTransition("ready", "out_for_delivery"))
        assertTrue(OrderStateMachine.canTransition("out_for_delivery", "delivered"))
    }

    @Test
    fun invalidTransitionsAreRejected() {
        assertFalse(OrderStateMachine.canTransition("pending", "delivered"))
        assertFalse(OrderStateMachine.canTransition("delivered", "pending"))
        assertFalse(OrderStateMachine.canTransition("cancelled", "accepted"))
    }

    @Test
    fun finalStatesAreRecognized() {
        assertTrue(OrderStateMachine.isFinal("delivered"))
        assertTrue(OrderStateMachine.isFinal("cancelled"))
        assertTrue(OrderStateMachine.isFinal("rejected"))
        assertTrue(OrderStateMachine.isFinal("failed"))
        assertFalse(OrderStateMachine.isFinal("pending"))
    }
}
