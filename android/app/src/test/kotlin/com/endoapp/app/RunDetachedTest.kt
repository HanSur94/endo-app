package com.endoapp.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression for the Check-In data loss: the screen launched its save in a composition scope and
 * dismissed at once, cancelling the save after the first row. [runDetached] must finish every write
 * even when the caller is cancelled mid-save.
 */
class RunDetachedTest {

    @Test
    fun cancellingTheCaller_doesNotCutTheWriteShort() = runBlocking {
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstRowWritten = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val written = mutableListOf<String>()

        val screenScope = CoroutineScope(Job() + Dispatchers.Default)
        screenScope.launch {
            runDetached(writeScope) {
                written += "dayEntry"
                firstRowWritten.complete(Unit)
                repeat(4) { i -> yield(); written += "row$i" }
                done.complete(Unit)
            }
        }

        firstRowWritten.await()
        screenScope.coroutineContext[Job]!!.cancelAndJoin() // the screen is dismissed mid-save
        withTimeout(5_000) { done.await() }

        assertEquals(listOf("dayEntry", "row0", "row1", "row2", "row3"), written)
    }
}
