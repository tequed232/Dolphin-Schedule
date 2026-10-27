package com.dolphin.schedule.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.NativeActivity
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.appearance.decodeSavedBackground
import com.dolphin.schedule.ui.MainTab
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource

/** 还原主界面时验证真实导航与日期状态；合成数据仅允许写入隔离模拟器。 */
class NativeHomeStyleTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private lateinit var repository: NativeRepository
    private lateinit var original: AppData
    private lateinit var model: DolphinViewModel
    private val date=LocalDate.now().plusDays(1)
    @Before fun prepare() {
        repository=NativeRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            val state=repository.initialize() as RepositoryState.Ready;original=state.snapshot.data
            val schedule=defaultSchedule().let { it.copy(term=it.term.copy(name="原生样式验收",startDate=firstMonday(date.toString()).toString()),courses=listOf(
                Course(id="style-english",name="大学英语（一）",teacher="合成教师",room="27栋305号教室",day=date.dayOfWeek.value,start=1,end=2,weeks=(1..20).toList(),color="blue"),
                Course(id="style-finance",name="智慧财经素养",teacher="合成导师",room="16栋203号教室",day=date.dayOfWeek.value,start=3,end=4,weeks=(1..20).toList(),color="lavender"),
            )) }
            repository.update { AppData(schedule=schedule,onboarded=true,settings=Preferences(mode="dark",dynamicColor=false)) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.selectDate(date);model.select(MainTab.LIST) }
        compose.waitForIdle()
    }
    @After fun restore() { if(::original.isInitialized)runBlocking { repository.update { original } } }

    @Test fun originalHomeSectionsKeepActionsAndDateState() {
        compose.onNodeWithTag("home-date-panel").assertIsDisplayed()
        compose.onNodeWithTag("home-next-course").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("编辑课程").isDisplayed() }
        compose.onNodeWithText("编辑课程").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { model.detailId=null }
        val heading=compose.onNodeWithText("日课表").captureToImage().toPixelMap()
        assertTrue("深色界面标题应使用浅色文字",(0 until heading.width).any { x -> (0 until heading.height).any { y -> heading[x,y].let { it.red>.8f && it.green>.8f && it.blue>.8f } } })
        saveScreen("native-preserved-home-dark.png")
        compose.onNodeWithContentDescription("平铺").performClick()
        compose.onNodeWithText("合成教师").assertIsDisplayed()
        saveScreen("native-preserved-grid-dark.png")
        compose.onNodeWithContentDescription("列表").performClick()
        compose.onNodeWithContentDescription("下一周").performClick()
        compose.runOnIdle { assertEquals(date.plusWeeks(1),model.selectedDate) }
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[DolphinViewModel::class.java];assertEquals(date.plusWeeks(1),model.selectedDate) }
        compose.onNodeWithContentDescription("打开月历").performClick()
        compose.onNodeWithText("选择日期").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("导入课表").performClick()
        compose.runOnIdle { assertEquals(NativePage.IMPORT,model.pages.last()) }
    }

    @Test fun lightLargeTypeAndWeekendSelectionRemainUsable() {
        runBlocking { repository.update { it.copy(settings=it.settings.copy(mode="light",scale=1.3f,showWeekend=false)) } }
        val saturday=firstMonday(date.toString()).plusDays(5)
        compose.activityRule.scenario.onActivity { model.selectDate(saturday) }
        compose.onNodeWithContentDescription("${saturday.year}年${saturday.monthValue}月${saturday.dayOfMonth}日 周六").assertIsSelected()
        compose.onNodeWithTag("home-date-strip").performTouchInput { swipeLeft(durationMillis=400) }
        val sunday=saturday.plusDays(1)
        compose.onNodeWithTag("home-date-strip").performScrollToIndex(DateStripRange(true).indexOf(sunday))
        compose.onNodeWithContentDescription("${sunday.year}年${sunday.monthValue}月${sunday.dayOfMonth}日 周日").performClick()
        compose.runOnIdle { assertEquals(sunday,model.selectedDate) }
        compose.onNodeWithContentDescription("添加课程").performClick()
        compose.onNodeWithText("添加课程").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("课表管理").performClick()
        compose.runOnIdle { assertEquals(NativePage.MANAGE,model.pages.last()) }
        compose.activityRule.scenario.onActivity { model.back();model.selectDate(date) }
        saveScreen("native-preserved-home-light-large.png")
        runBlocking { repository.update { it.copy(settings=it.settings.copy(showWeekend=true)) } }
        compose.onNodeWithContentDescription("平铺").performClick()
        compose.onNodeWithText("合成教师").assertIsDisplayed()
        saveScreen("native-preserved-grid-light-large.png")
    }

    @Test fun savedBackgroundIsLocalBoundedAndNeverBundled() {
        assertNull(decodeSavedBackground("https://example.invalid/image.jpg",10))
        assertNull(decodeSavedBackground("data:image/svg+xml;base64,PHN2Zz4=",10))
        assertNull(decodeSavedBackground("data:image/png;base64,broken",10))
        compose.onNodeWithTag("saved-background").assertDoesNotExist()
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes=ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray();bitmap.recycle()
        val url="data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
        val decoded=decodeSavedBackground(url,12)!!;assertEquals(64,decoded.width);decoded.recycle()
        runBlocking { repository.update { it.copy(background=Background(url,"合成背景",64,64,64,64),settings=it.settings.copy(backgroundEnabled=true,backgroundBlur=12)) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("saved-background").fetchSemanticsNodes().size==1 }
        compose.onNodeWithContentDescription("添加课程").assertIsDisplayed()
        runBlocking { repository.update { it.copy(settings=it.settings.copy(backgroundEnabled=false)) } }
        compose.onNodeWithTag("saved-background").assertDoesNotExist()
    }

    private fun saveScreen(name: String) {
        compose.mainClock.advanceTimeBy(800);compose.waitForIdle();android.os.SystemClock.sleep(300)
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
