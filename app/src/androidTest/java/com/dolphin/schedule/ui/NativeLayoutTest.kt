package com.dolphin.schedule.ui

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
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
import java.time.LocalDate

/** 真实触摸与事务写入一起验证；合成课表只允许进入隔离模拟器。 */
class NativeLayoutTest {
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
    private val monday=firstMonday(LocalDate.now().toString())
    @Before fun prepare() {
        repository=NativeRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            original=(repository.initialize() as RepositoryState.Ready).snapshot.data
            repository.update { AppData(onboarded=true,settings=Preferences(dynamicColor=false,showWeekend=false),schedule=Schedule(
                Term("合成拖动",monday.toString(),21),
                listOf(Period("08:30","09:10","1"),Period("09:15","09:55","2"),Period("10:15","10:55","3"),Period("11:00","11:40","4"),Period("12:10","12:50","中午1"),Period("12:55","13:35","中午2"),Period("14:10","14:50","5"),Period("14:55","15:35","6"),Period("15:55","16:35","7"),Period("16:40","17:20","8")),
                listOf(Course(id="move",name="拖动验收课程",teacher="合成教师",room="合成教室",day=1,start=2,end=3,weeks=listOf(1,3,5),notes="保留备注",color="blue"),Course(id="collision",name="冲突验收课程",day=2,start=3,end=4,weeks=listOf(1,3,5))),
            )) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java];model.pages.clear();model.selectDate(monday);model.select(MainTab.GRID) }
        compose.waitForIdle()
    }
    @After fun restore() { if(::original.isInitialized)runBlocking { repository.update { original } } }
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data
    private fun moving()=data().schedule.courses.first { it.id=="move" }
    private fun card()=compose.onNodeWithTag("layout-course-move")
    private fun rows()=displayTimetableRows(data().schedule)
    private fun pixels(dp: Float)=dp*compose.activity.resources.displayMetrics.density
    private fun moveGesture(x: Float=0f,y: Float=0f) {
        card().performTouchInput { down(center);advanceEventTime(650);moveBy(Offset(x,y),delayMillis=120);up() }
        compose.waitForIdle()
    }
    private fun saved(condition: ()->Boolean) { compose.waitUntil(10_000) { !model.layout.busy && condition() } }

    @Test fun longPressSelectsHandlesAndCancelNeverWrites() {
        val before=data().schedule
        card().performTouchInput { longClick() }
        compose.onNodeWithTag("layout-start").assertIsDisplayed()
        compose.onNodeWithTag("layout-end").assertIsDisplayed()
        assertEquals(before,data().schedule)
        compose.onNodeWithTag("layout-done").performClick()
        compose.onNodeWithTag("layout-start").assertDoesNotExist()
        assertNull(model.layout.selectedId)
        // 取消正在进行的移动，不能把预览写入课表。
        card().performTouchInput { down(center);advanceEventTime(650);moveBy(Offset(0f,pixels(90f)),120);cancel() }
        compose.waitForIdle()
        assertEquals(before,data().schedule)
        assertNull(model.layout.draft)
        compose.runOnIdle { model.layout.exit() }
        card().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("编辑课程").isDisplayed() }
        compose.onNodeWithText("编辑课程").performScrollTo().assertIsDisplayed()
    }

    @Test fun holdingAtEdgesScrollsBothAxesAndCancelRestoresOriginal() {
        val before=data().schedule
        val start=card().fetchSemanticsNode().boundsInRoot.center
        val board=compose.onNodeWithTag("timetable-board").fetchSemanticsNode().boundsInRoot
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        // 持续按住时本来就有逐帧边缘滚动，不能等待这段动画自行空闲。
        compose.mainClock.autoAdvance=false
        try {
            compose.onRoot().performTouchInput {
                down(start);advanceEventTime(650)
                moveTo(Offset(board.right-4f,root.bottom-pixels(20f)),120)
            }
            compose.mainClock.advanceTimeBy(1_200)
            compose.runOnUiThread {
                val preview=model.layout.draft?.next
                assertNotNull("边缘滚动期间应保留手势预览",preview)
                assertTrue("水平边缘应可拖到原本屏外的周末",preview!!.day>=6)
                assertTrue("垂直边缘应可继续拖到下午",preview.start>=7)
            }
        } finally {
            compose.onRoot().performTouchInput { cancel() }
            compose.mainClock.autoAdvance=true
        }
        compose.waitForIdle()
        assertEquals(before,data().schedule)
        assertNull(model.layout.draft)
    }

    @Test fun dragMovesAndPersistsOnlyPosition() {
        val before=moving()
        val width=card().fetchSemanticsNode().boundsInRoot.width
        moveGesture(width+pixels(4f),-pixels(rows()[0].height))
        saved { moving().day==2 && moving().start==1 }
        assertEquals(before.copy(day=2,start=1,end=2),moving())
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[DolphinViewModel::class.java] }
        card().assertIsDisplayed()
        assertEquals(before.copy(day=2,start=1,end=2),moving())
    }

    @Test fun handlesResizeIndependentlyAndProtectOtherFields() {
        val before=moving()
        card().performTouchInput { longClick() }
        compose.onNodeWithTag("layout-end").performTouchInput { swipe(center,center+Offset(0f,pixels(rows()[2].height)),300) }
        saved { moving().end==4 }
        assertEquals(before.copy(end=4),moving())
        compose.onNodeWithTag("layout-start").performTouchInput { swipe(center,center-Offset(0f,pixels(rows()[0].height)),300) }
        saved { moving().start==1 }
        assertEquals(before.copy(start=1,end=4),moving())
    }

    @Test fun collisionRequiresConfirmationAndStaleCourseCannotBeOverwritten() {
        val before=moving()
        val width=card().fetchSemanticsNode().boundsInRoot.width
        moveGesture(width+pixels(4f),pixels(rows()[1].height))
        compose.onNodeWithText("调整后与其他课程重叠").assertIsDisplayed()
        assertEquals(before,moving())
        compose.onNodeWithText("取消调整").performClick()
        assertEquals(before,moving())
        moveGesture(width+pixels(4f),pixels(rows()[1].height))
        compose.onNodeWithText("仍然保存").performClick()
        saved { moving().day==2 && moving().start==3 }
        assertEquals(before.copy(day=2,start=3,end=4),moving())
        val latest=data().schedule
        compose.runOnIdle { model.layout.begin(latest,moving(),monday.plusDays(1),LayoutEdge.MOVE);model.layout.preview(1,0f,rows()) }
        runBlocking { repository.update { it.copy(schedule=it.schedule.copy(courses=it.schedule.courses.map { c -> if(c.id=="move")c.copy(notes="另一处已修改") else c })) } }
        compose.runOnIdle { model.layout.finish() }
        compose.onNodeWithText("位置未保存").assertIsDisplayed()
        assertEquals(2,moving().day)
        assertEquals("另一处已修改",moving().notes)
    }

    @Test fun ordinaryCourseSwipeBrowsesWeekWithoutMovingCourse() {
        val before=data().schedule
        card().performTouchInput { swipeLeft(durationMillis=200) }
        repeat(4) {
            val boards=compose.onAllNodesWithTag("timetable-board")
            val visible=(boards.fetchSemanticsNodes().indices).first { index -> boards[index].isDisplayed() }
            boards[visible].performTouchInput { swipeLeft(durationMillis=200) }
        }
        compose.waitUntil(5_000) { model.selectedDate.isAfter(monday) }
        assertEquals(before,data().schedule)
        assertNull(model.layout.selectedId)
        assertNull(model.edit)
    }

    @Test fun oneHorizontalGestureKeepsOwnershipAtEdgeAndImmediateReverse() {
        val before=data().schedule
        val viewport=compose.onNodeWithTag("week-pager").fetchSemanticsNode().boundsInRoot
        val start=Offset(viewport.right-pixels(36f),viewport.top+pixels(140f))
        fun offset()=compose.onNodeWithTag("week-days-$monday").fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        compose.mainClock.autoAdvance=false
        try {
            compose.onRoot().performTouchInput { down(start);moveBy(Offset(-pixels(100f),0f),80) }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("课程块上的普通横滑应浏览星期",offset()>0f)
            compose.onRoot().performTouchInput { moveBy(Offset(-pixels(200f),0f),80) }
            compose.mainClock.advanceTimeByFrame()
            val edge=offset()
            assertEquals("同一次手势抵达边界不能突然变成翻周",monday,model.selectedDate)
            compose.onRoot().performTouchInput { moveBy(Offset(pixels(70f),0f),80) }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("触边后立即反向应立即滚回，不能被外层分页吃掉",offset()<edge-pixels(30f))
        } finally {
            compose.onRoot().performTouchInput { cancel() }
            compose.mainClock.autoAdvance=true
        }
        compose.waitForIdle()
        assertEquals(monday,model.selectedDate)
        assertEquals(before,data().schedule)
        assertNull(model.layout.selectedId)
        assertNull(model.edit)
    }

    @Test fun edgeFlingAdvancesOneWeekAndOppositeFlingReturns() {
        val before=data().schedule
        val pager=compose.onNodeWithTag("week-pager")
        // 第一条手势只滚到周末，第二条才向外翻页；高速甩动最多翻一周。
        pager.performTouchInput { swipeLeft(durationMillis=100) }
        assertEquals(monday,model.selectedDate)
        pager.performTouchInput { swipeLeft(durationMillis=100) }
        compose.waitUntil(5_000) { model.selectedDate==monday.plusWeeks(1) }
        pager.performTouchInput { swipeRight(durationMillis=100) }
        compose.waitUntil(5_000) { model.selectedDate==monday }
        assertEquals(before,data().schedule)
        assertNull(model.layout.selectedId)
        assertNull(model.edit)
    }

    @Test fun shortCancelledEdgeDragAndVerticalDiagonalDoNotFlipOrEdit() {
        val before=data().schedule
        val viewport=compose.onNodeWithTag("week-pager").fetchSemanticsNode().boundsInRoot
        val start=Offset(viewport.left+pixels(100f),viewport.top+pixels(150f))
        val originalLeft=card().fetchSemanticsNode().boundsInRoot.left
        compose.onRoot().performTouchInput { down(start);moveBy(Offset(pixels(36f),0f),180);cancel() }
        compose.waitForIdle()
        assertEquals("短距离取消应回到原周",monday,model.selectedDate)
        assertEquals("取消后页面也应完整吸附，不能停留在两周之间",originalLeft,card().fetchSemanticsNode().boundsInRoot.left,pixels(1f))
        compose.onRoot().performTouchInput { swipe(start,start+Offset(-pixels(20f),-pixels(120f)),240) }
        compose.waitForIdle()
        assertEquals("以纵向为主的斜滑不应改变周次",monday,model.selectedDate)
        val headerLeft=compose.onNodeWithTag("week-days-$monday").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("week-pager").performTouchInput { swipeRight(durationMillis=200) }
        compose.waitUntil(5_000) { model.selectedDate==monday.minusWeeks(1) }
        assertEquals("取消后的下一次完整手势应翻到相邻周并完整吸附",headerLeft,
            compose.onNodeWithTag("week-days-${monday.minusWeeks(1)}").fetchSemanticsNode().boundsInRoot.left,pixels(1f))
        assertEquals(before,data().schedule)
        assertNull(model.layout.selectedId)
        assertNull(model.edit)
    }
}
