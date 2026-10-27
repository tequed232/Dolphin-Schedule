package com.dolphin.schedule.ui

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.MainTab
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** 写入合成数据的测试必须显式启用，并且只允许隔离模拟器。 */
class NativeFlowTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true") { "需显式允许原生数据测试" }
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_")) { "禁止在用户真机改写课表" }
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private lateinit var original: AppData
    private lateinit var repository: NativeRepository
    private lateinit var model: DolphinViewModel
    private val monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    @Before fun prepareSyntheticSchedule() {
        repository=NativeRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            val initialized=repository.initialize()
            check(initialized is RepositoryState.Ready) { initialized.toString() }
            original=initialized.snapshot.data
            val schedule=defaultSchedule().let { it.copy(term=it.term.copy(startDate=monday.toString()),courses=listOf(
                Course(id="native-test-algorithms",name="算法基础",teacher="测试教师",room="A101",day=1,start=1,end=2,weeks=listOf(1,2,3)),
                Course(id="native-test-design",name="设计实践",teacher="测试导师",room="B202",day=3,start=3,end=4,weeks=listOf(1,2,3),color="lavender"),
            )) }
            repository.update { AppData(schedule=schedule,onboarded=true,settings=Preferences(dynamicColor=false)) }
        }
        compose.activityRule.scenario.onActivity { activity -> model=ViewModelProvider(activity)[DolphinViewModel::class.java];model.selectDate(monday);model.select(MainTab.LIST) }
        compose.waitUntil(10_000) { (repository.state.value as? RepositoryState.Ready)?.snapshot?.data?.schedule?.courses?.size==2 && !model.busy }
    }
    @After fun restoreEmulatorData() {
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }
    @Test fun capsuleRoutesToSearchAndNativeCourseChangesPersist() {
        compose.onAllNodesWithText("算法基础").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("capsule-navigation").assertWidthIsEqualTo(320.dp)
        compose.onNodeWithContentDescription("搜索").performClick().assertIsSelected()
        compose.onNodeWithText("课程、教师或教室").performTextInput("A101")
        compose.onNodeWithText("算法基础").performClick()
        compose.onNodeWithText("编辑课程").performClick()
        compose.onNode(hasSetTextAction() and hasText("测试教师")).performTextReplacement("原生编辑教师")
        compose.onNodeWithText("保存",useUnmergedTree=true).performClick()
        compose.waitUntil(10_000) { (repository.state.value as? RepositoryState.Ready)?.snapshot?.data?.schedule?.courses?.firstOrNull { it.id=="native-test-algorithms" }?.teacher=="原生编辑教师" && !model.busy }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("搜索").assertIsSelected()
        compose.onNodeWithText("原生编辑教师").assertIsDisplayed()
        saveScreen("native-search.png")
    }
    @Test fun cardHorizontalBrowseReachesNextWeekWithoutOpeningAnEditor() {
        compose.onNodeWithContentDescription("平铺").performClick().assertIsSelected()
        compose.onNodeWithText("算法基础").assertIsDisplayed()
        saveScreen("native-week-start.png")
        compose.onNodeWithText("算法基础").performTouchInput { swipeLeft(durationMillis=450) }
        compose.waitForIdle()
        assertNull(model.edit)
        assertEquals(monday,model.selectedDate)
        // 窄屏先浏览剩余星期，抵达边缘后继续滑动应交给外层翻周。
        repeat(4) { compose.onRoot().performTouchInput { swipe(Offset(width*.88f,height*.4f),Offset(width*.2f,height*.4f),durationMillis=500) };compose.waitForIdle() }
        compose.waitUntil(5_000) { model.selectedDate.isAfter(monday) }
        assertNull(model.edit)
        saveScreen("native-week.png")
    }
    private fun saveScreen(name: String) {
        compose.mainClock.advanceTimeBy(800);compose.waitForIdle();android.os.SystemClock.sleep(400)
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
