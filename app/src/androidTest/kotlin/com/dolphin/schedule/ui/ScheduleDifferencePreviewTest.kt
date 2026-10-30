package com.dolphin.schedule.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.common.DolphinTheme
import com.dolphin.schedule.ui.importing.SchedulePreviewSummary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 只在内存展示合成预览；没有仓库、保存或学校会话。 */
class ScheduleDifferencePreviewTest {
    @get:Rule val compose=createComposeRule()
    private val course=Course(id="old",name="差异课程",teacher="原教师",room="1栋101",day=1,start=1,end=2,weeks=listOf(1,3))
    private val before=defaultSchedule().copy(term=Term("合成学期","2026-09-01",20),courses=listOf(course))
    private fun detailsScroll(text: String) {
        compose.onNodeWithTag("schedule-difference-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    @Test fun changedRoomShowsBeforeAfterAndClosingNeverChangesTheSchedules() {
        val after=before.copy(courses=listOf(course.copy(id="new",room="2栋201")))
        compose.setContent { DolphinTheme(Preferences(mode="light",dynamicColor=false,scale=1.35f)) { SchedulePreviewSummary(before,after) } }
        compose.onNodeWithText("新增 0 · 修改 1 · 移除 0").assertIsDisplayed()
        compose.onNodeWithText("查看变更明细").performClick()
        detailsScroll("原来：1栋101");detailsScroll("现在：2栋201")
        compose.onNodeWithTag("schedule-difference-list").performScrollToNode(hasText("返回核对"))
        compose.onNodeWithText("返回核对").performClick()
        compose.onNodeWithTag("schedule-difference-list").assertDoesNotExist()
        assertEquals("1栋101",before.courses.single().room);assertEquals("2栋201",after.courses.single().room)
    }
    @Test fun unchangedPreviewHasNoFalseChangePrompt() {
        compose.setContent { DolphinTheme(Preferences(dynamicColor=false)) { SchedulePreviewSummary(before,before.copy(courses=listOf(course.copy(id="other")))) } }
        compose.onNodeWithText("课程、学期与作息没有变化").assertIsDisplayed()
        compose.onNodeWithText("查看变更明细").assertDoesNotExist()
    }
    @Test fun longChangeListCanScrollToLastRemovalAndTermAndTimesHaveOldAndNewValues() {
        val old=before.copy(courses=(1..120).map { course.copy(id="old-$it",name="移除课程$it") })
        val after=before.copy(term=before.term.copy(weeks=21),periods=before.periods.mapIndexed { index,p -> if(index==0)p.copy(start="08:05") else p },courses=emptyList())
        compose.setContent { DolphinTheme(Preferences(mode="dark",dynamicColor=false)) { SchedulePreviewSummary(old,after) } }
        compose.onNodeWithText("查看变更明细").performClick()
        detailsScroll("原来：20 周");detailsScroll("现在：21 周")
        detailsScroll("原来：第 1 节 08:00—08:45");detailsScroll("现在：第 1 节 08:05—08:45")
        detailsScroll("移除课程120")
    }
}
