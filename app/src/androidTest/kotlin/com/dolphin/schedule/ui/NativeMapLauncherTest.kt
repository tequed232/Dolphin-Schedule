package com.dolphin.schedule.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.maps.MapLauncher
import org.junit.Assert.*
import org.junit.Test

class NativeMapLauncherTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val target get()=courseDestination(Course(name="课程",room="1-401 (QY1-401)",day=1,start=1,end=2,weeks=listOf(1)),Preferences(school="合成学校"))
    @Test fun clientIntentIsPackageScopedAndDoesNotOpenAWebPageFirst() {
        val opened=mutableListOf<Intent>()
        MapLauncher.open(context,target.url,opened::add)
        val intent=opened.single()
        assertEquals("com.autonavi.minimap",intent.`package`)
        assertEquals(mapLaunchPlan(target.url).nativeUrl,intent.data.toString())
        assertTrue(intent.hasCategory(Intent.CATEGORY_DEFAULT))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK!=0)
    }
    @Test fun missingClientOrBlockedClientFallsBackToTheUnchangedWebTarget() {
        listOf(ActivityNotFoundException("missing"),SecurityException("blocked")).forEach { failure ->
            val opened=mutableListOf<Intent>()
            MapLauncher.open(context,target.url) { intent ->
                opened+=intent
                if(intent.`package`!=null)throw failure
            }
            assertEquals(2,opened.size)
            assertEquals("com.autonavi.minimap",opened[0].`package`)
            assertNull(opened[1].`package`);assertEquals(target.url,opened[1].data.toString())
        }
    }
    @Test fun noWebHandlerPropagatesFailureInsteadOfReportingASuccessfulLaunch() {
        assertThrows(ActivityNotFoundException::class.java) {
            MapLauncher.open(context,target.url) { throw ActivityNotFoundException("unavailable") }
        }
    }
    @Test fun baiduDoesNotAttemptToOpenAmap() {
        val opened=mutableListOf<Intent>()
        val web=courseDestination(Course(name="课程",room="1栋401",day=1,start=1,end=2,weeks=listOf(1)),Preferences(school="合成学校",map="baidu")).url
        MapLauncher.open(context,web,opened::add)
        assertNull(opened.single().`package`);assertEquals(web,opened.single().data.toString())
    }
}
