package com.dolphin.schedule.ui

import com.dolphin.schedule.domain.RELEASES_URL
import com.dolphin.schedule.domain.ReleaseInfo
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SupportControllerTest {
    private val release=ReleaseInfo("v1.6.0","正式版本","更新说明","$RELEASES_URL/tag/v1.6.0","")

    @Test fun checkIsExplicitAndConcurrentRequestsAreCoalesced()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val pending=CompletableDeferred<ReleaseInfo?>();var calls=0
            val controller=SupportController(scope,"1.5.5") { calls++;pending.await() }
            assertEquals(0,calls)
            controller.ensureChecked();controller.check();controller.ensureChecked()
            assertEquals(1,calls);assertTrue(controller.busy)
            pending.complete(release)
            assertFalse(controller.busy);assertTrue(controller.checked);assertEquals(release,controller.release)
            controller.ensureChecked();assertEquals(1,calls)
            controller.check();assertEquals(2,calls)
        } finally { scope.cancel() }
    }

    @Test fun failedRetryPreservesLastReleaseAndSuccessfulTime()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var fail=false
            val controller=SupportController(scope,"1.5.5") { if(fail)error("合成网络错误") else release }
            controller.check();val checkedAt=controller.checkedAt
            fail=true;controller.check()
            assertEquals("合成网络错误",controller.error);assertEquals(release,controller.release)
            assertEquals(checkedAt,controller.checkedAt);assertFalse(controller.busy)
        } finally { scope.cancel() }
    }

    @Test fun noPublishedReleaseIsDistinctFromFailedCheck()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val controller=SupportController(scope,"1.5.5") { null }
            controller.check()
            assertTrue(controller.noRelease);assertTrue(controller.checked);assertNull(controller.error);assertNull(controller.release)
        } finally { scope.cancel() }
    }
}
