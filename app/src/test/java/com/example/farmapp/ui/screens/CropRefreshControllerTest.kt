package com.example.farmapp.ui.screens

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CropRefreshControllerTest {
    @Test fun concurrentTriggersStartExactlyOneLoad() = runBlocking {
        val controller = CropRefreshController()
        val response = CompletableDeferred<List<String>>()
        var calls = 0
        var visible = listOf("old")
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            controller.refresh({ calls++; response.await() }, { visible = it }, { throw AssertionError(it) })
        }
        assertTrue(controller.refreshing)
        repeat(3) {
            controller.refresh({ calls++; listOf("duplicate") }, { visible = it }, { throw AssertionError(it) })
        }
        assertEquals(1, calls)
        assertEquals(listOf("old"), visible)
        response.complete(listOf("latest"))
        first.join()
        assertEquals(listOf("latest"), visible)
        assertFalse(controller.refreshing)
    }

    @Test fun failedRefreshKeepsContentAndRetryClearsError() = runBlocking {
        val controller = CropRefreshController()
        var visible = listOf("existing crop")
        var error: Exception? = null
        val failure = IOException("Connection unavailable")
        controller.refresh<List<String>>({ throw failure }, { visible = it; error = null }, { error = it })
        assertEquals(listOf("existing crop"), visible)
        assertSame(failure, error)
        assertFalse(controller.refreshing)
        controller.refresh({ listOf("updated crop") }, { visible = it; error = null }, { error = it })
        assertEquals(listOf("updated crop"), visible)
        assertNull(error)
        assertFalse(controller.refreshing)
    }

    @Test fun emptyListCanRefreshAfterInitialFailureAndBecomeEmptyAgain() = runBlocking {
        val controller = CropRefreshController()
        var visible = emptyList<String>()
        var successes = 0
        val accept: (List<String>) -> Unit = { visible = it; successes++ }
        controller.refresh<List<String>>({ throw IOException() }, accept, {})
        assertTrue(visible.isEmpty())
        controller.refresh({ listOf("new crop") }, accept, { throw AssertionError(it) })
        assertEquals(listOf("new crop"), visible)
        controller.refresh({ emptyList<String>() }, accept, { throw AssertionError(it) })
        assertTrue(visible.isEmpty())
        assertEquals(2, successes)
        assertFalse(controller.refreshing)
    }

    @Test fun cancellationPropagatesAndReleasesGuardWithoutFailureFeedback() = runBlocking {
        val controller = CropRefreshController()
        var failed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            controller.refresh({ awaitCancellation() }, {}, { failed = true })
        }
        assertTrue(controller.refreshing)
        job.cancelAndJoin()
        assertFalse(controller.refreshing)
        assertFalse(failed)
        var completed = false
        controller.refresh({ Unit }, { completed = true }, { failed = true })
        assertTrue(completed)
    }
}
