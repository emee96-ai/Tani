package com.tani.app.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LifecycleCancellationTest {
    @Test fun stoppingARequestDoesNotInvokeTheScreenErrorHandler() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var errorCallbacks = 0
        val request = launch {
            runCatchingCancellable<Unit> {
                started.complete(Unit)
                awaitCancellation()
            }.onFailure { errorCallbacks++ }
        }
        started.await()
        request.cancelAndJoin()
        assertEquals(0, errorCallbacks)
    }
}
