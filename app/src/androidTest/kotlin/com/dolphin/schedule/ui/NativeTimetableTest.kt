package com.dolphin.schedule.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.MainTab
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** 时间编辑与删除使用合成课表；真机日用数据禁止进入这些用例。 */
class NativeTimetableTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private lateinit var original: AppData
    private lateinit var model: DolphinViewModel
    private lateinit var repository: NativeRepository
    @Before fun prepare() {
        repository=NativeRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            original=(repository.initialize() as RepositoryState.Ready).snapshot.data
            val today=LocalDate.now()
            repository.update { AppData(onboarded=true,settings=Preferences(dynamicColor=false),schedule=Schedule(
                Term("合成作息",firstMonday(today.toString()).toString(),21),
                listOf(Period("00:00","11:40","4"),Period("12:10","12:50","中午1"),Period("12:55","13:35","中午2"),Period("14:10","23:59","5")),
                listOf(Course(id="timing",name="合成下午课程",day=today.dayOfWeek.value,start=4,end=4,weeks=(1..21).toList())),
            )) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.selectDate(LocalDate.now());model.select(MainTab.GRID) }
        compose.waitForIdle()
    }
    @After fun restore() { if(::original.isInitialized)runBlocking { repository.update { original } } }
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data
    @Test fun noonPlaceholdersCollapseClockLineTracksTodayAndEditingCanCancel() {
        compose.onNodeWithTag("period-1").assertDoesNotExist()
        compose.onNodeWithTag("period-2").assertDoesNotExist()
        compose.onNodeWithTag("current-time-line").assertIsDisplayed()
        compose.onNodeWithTag("period-0").performClick()
        compose.onNodeWithText("编辑上课时间").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertNull(model.periodEdit)
        compose.runOnUiThread { model.moveDate(7) }
        compose.onNodeWithTag("current-time-line").assertDoesNotExist()
    }
    @Test fun deletingUnusedPeriodRemapsCourseAndOccupiedPeriodIsProtected() {
        val before=data()
        compose.runOnUiThread { model.openPeriod(data().schedule,1) }
        compose.onNodeWithText("删除这个节次").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { !model.busy && data().schedule.periods.size==3 }
        assertEquals(3,data().schedule.courses.single().start)
        assertEquals(before.schedule.periods[3],data().schedule.periods[2])
        compose.runOnUiThread { model.openPeriod(data().schedule,2) }
        compose.onNodeWithText("删除这个节次").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.onNodeWithText("这个节次有课程使用，请先调整相关课程，再删除时间").assertIsDisplayed()
        assertEquals(3,data().schedule.periods.size)
    }
    @Test fun timeEditPersistsAndStaleDialogCannotOverwriteAnotherChange() {
        compose.runOnUiThread { model.openPeriod(data().schedule,3) }
        compose.onNode(hasSetTextAction() and hasText("14:10")).performTextReplacement("14:15")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { !model.busy && data().schedule.periods.last().start=="14:15" }
        compose.runOnUiThread { model.openPeriod(data().schedule,3) }
        runBlocking { repository.update { it.copy(schedule=it.schedule.copy(term=it.schedule.term.copy(name="另一处已修改"))) } }
        compose.onNode(hasSetTextAction() and hasText("14:15")).performTextReplacement("14:20")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { !model.busy && model.error!=null }
        assertEquals("14:15",data().schedule.periods.last().start)
        assertNotNull(model.periodEdit)
    }
}
