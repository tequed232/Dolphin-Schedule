package com.dolphin.schedule.ui

import android.os.Build
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 验证正式入口的引导路由和持久化；合成数据只允许写入显式启用的隔离模拟器。 */
class NativeSupportFlowTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true") { "需显式允许原生数据测试" }
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_")) { "禁止在用户真机改写课表" }
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private lateinit var repository: NativeRepository
    private lateinit var original: AppData
    private lateinit var fixture: AppData
    private lateinit var model: DolphinViewModel

    @Before fun prepareIsolatedData() {
        repository=NativeRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            val initialized=repository.initialize()
            check(initialized is RepositoryState.Ready) { initialized.toString() }
            original=initialized.snapshot.data
            val monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val course=Course(id="support-flow-course",name="引导保留课程",room="教学楼 201",day=1,start=1,end=2,weeks=listOf(1,2))
            fixture=AppData(
                schedule=defaultSchedule().let { it.copy(term=it.term.copy(name="引导保留课表",startDate=monday.toString()),courses=listOf(course)) },
                books=mapOf(course.id to Book(title="引导保留教材",publisher="测试出版社")),
                settings=Preferences(dynamicColor=false,school="测试学校"),
                onboarded=false,
            )
            repository.update { fixture }
        }
        refreshModel()
        compose.activityRule.scenario.onActivity { model.select(MainTab.LIST) }
        compose.onNodeWithTag("native-welcome").assertIsDisplayed()
        compose.onNodeWithTag("capsule-navigation").assertDoesNotExist()
    }

    @After fun restoreEmulatorData() {
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }

    @Test fun continuePersistsAndReopeningGuideFromAboutCanBeCancelledWithoutDataLoss() {
        compose.onNodeWithTag("welcome-continue").performScrollTo().performClick()
        awaitCompletedGuide()
        recreateActivity()
        compose.onNodeWithTag("native-welcome").assertDoesNotExist()
        compose.onNodeWithTag("capsule-navigation").assertIsDisplayed()
        assertEquals(fixture.copy(onboarded=true),currentData())

        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("导航与学校").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关于").performScrollTo().assertIsDisplayed()
        // performScrollTo 只知道滚动容器的视口；悬浮胶囊仍可能盖住底部条目。
        // 像实际用户一样继续上滑到末尾，确保目标完整位于胶囊上方后再点击。
        compose.onRoot().performTouchInput { swipeUp(durationMillis=300) }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithText("关于").fetchSemanticsNode().boundsInRoot.bottom < compose.onNodeWithTag("capsule-navigation").fetchSemanticsNode().boundsInRoot.top)
        listOf("系统日历同步","日历同步","节假日","假期").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        assertFalse(NativePage.entries.any { it.name in setOf("CALENDAR","HOLIDAYS") })
        compose.onNodeWithText("关于").performClick()
        try { compose.waitUntil(5_000) { compose.onNodeWithTag("native-about").isDisplayed() } }
        catch(failure: Throwable) {
            println("Native route: ${model.pages}, guide=${model.showGuide}, tab=${model.tab}")
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { index -> println(compose.onAllNodes(isRoot())[index].printToString()) }
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"support-about-route-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            throw failure
        }
        compose.onNodeWithTag("native-about").assertIsDisplayed()
        compose.onNodeWithTag("about-guide").performScrollTo().performClick()
        compose.onNodeWithTag("native-welcome").assertIsDisplayed()
        compose.onNodeWithTag("capsule-navigation").assertDoesNotExist()

        // 重看引导的系统返回应回到关于页，不应清空已有课程、教材或偏好。
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitCompletedGuide()
        compose.onNodeWithTag("native-about").assertIsDisplayed()
        assertEquals(NativePage.ABOUT,model.pages.lastOrNull())
        assertEquals(fixture.copy(onboarded=true),currentData())
        recreateActivity()
        compose.onNodeWithTag("native-about").assertIsDisplayed()
        compose.onNodeWithTag("native-welcome").assertDoesNotExist()
    }

    @Test fun importChoiceUsesActualImportRouteAndSurvivesActivityRecreation() {
        compose.onNodeWithTag("welcome-import").performScrollTo().performClick()
        awaitCompletedGuide()
        assertEquals(NativePage.IMPORT,model.pages.lastOrNull())
        compose.onNodeWithText("打开学校教务").assertIsDisplayed()
        compose.onNodeWithText("从文件导入 · JSON / CSV / Excel").assertIsDisplayed()
        assertNull(model.academicPending)
        assertEquals(fixture.copy(onboarded=true),currentData())
        recreateActivity()
        assertEquals(NativePage.IMPORT,model.pages.lastOrNull())
        compose.onNodeWithText("打开学校教务").assertIsDisplayed()
        compose.onNodeWithTag("native-welcome").assertDoesNotExist()
        assertEquals(fixture.copy(onboarded=true),currentData())
    }

    @Test fun manualChoiceOpensUnsavedNativeEditorAndCancelKeepsExistingData() {
        compose.onNodeWithTag("welcome-manual").performScrollTo().performClick()
        awaitCompletedGuide()
        compose.onNodeWithText("添加课程").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("课程名称")).assertIsDisplayed()
        compose.runOnIdle { assertNotNull(model.edit);assertNull(model.edit?.original) }
        assertEquals(fixture.copy(onboarded=true),currentData())
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(5_000) { model.edit==null }
        compose.onNodeWithTag("capsule-navigation").assertIsDisplayed()
        recreateActivity()
        compose.runOnIdle { assertNull(model.edit) }
        compose.onNodeWithTag("native-welcome").assertDoesNotExist()
        assertEquals(fixture.copy(onboarded=true),currentData())
    }

    private fun currentData(): AppData=(repository.state.value as RepositoryState.Ready).snapshot.data
    private fun awaitCompletedGuide() {
        compose.waitUntil(10_000) { currentData().onboarded && !model.busy && !model.showGuide }
        compose.waitForIdle()
    }
    private fun refreshModel() {
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java] }
    }
    private fun recreateActivity() {
        compose.activityRule.scenario.recreate()
        refreshModel()
        compose.waitForIdle()
    }
}
