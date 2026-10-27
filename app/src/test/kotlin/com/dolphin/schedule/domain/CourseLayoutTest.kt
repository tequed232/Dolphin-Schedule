package com.dolphin.schedule.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CourseLayoutTest {
    private val course=Course(id="move",name="合成课程",day=1,start=1,end=2,weeks=listOf(1,3),notes="保留备注",teacher="合成教师")
    private val schedule=Schedule(Term("合成学期","2026-10-26",21),listOf(Period("08:00","08:40","1"),Period("08:50","09:30","2"),Period("12:10","12:50","中午1"),Period("14:00","14:40","3"),Period("14:50","15:30","4")),listOf(course))
    private val date=LocalDate.of(2026,10,26)
    @Test fun movingSkipsCollapsedNoonAndRetainsDurationWeeksAndMetadata() {
        val rows=displayTimetableRows(schedule)
        val result=layoutCandidate(schedule,course,date,LayoutEdge.MOVE,2,rows.take(3).sumOf { it.height.toDouble() }.toFloat(),rows)
        assertEquals(course.copy(day=3,start=4,end=5),result)
        assertEquals(course,layoutCandidate(schedule,course,date,LayoutEdge.MOVE,-50,-9999f,rows))
        assertEquals(7,layoutCandidate(schedule,course,date,LayoutEdge.MOVE,Int.MAX_VALUE,0f,rows).day)
    }
    @Test fun resizingOnlyChangesOneEdgeAndDoesNotBridgeHiddenLunch() {
        val rows=displayTimetableRows(schedule)
        assertEquals(1,layoutCandidate(schedule,course,date,LayoutEdge.END,4,-999f,rows).end)
        assertEquals(2,layoutCandidate(schedule,course,date,LayoutEdge.END,4,999f,rows).end)
        assertEquals(course.copy(start=2),layoutCandidate(schedule,course,date,LayoutEdge.START,0,999f,rows))
    }
    @Test fun temporaryCourseDateMovesWithinItsDisplayedWeek() {
        val temporary=course.copy(temporary=true,specificDate=date.toString())
        val next=layoutCandidate(schedule.copy(courses=listOf(temporary)),temporary,date,LayoutEdge.MOVE,3,0f,displayTimetableRows(schedule))
        assertEquals("2026-10-29",next.specificDate);assertEquals(4,next.day);assertTrue(next.temporary)
    }
    @Test fun commitRejectsStaleCourseAndTimeChangesButRetainsUnrelatedEdits() {
        val next=course.copy(day=3)
        val other=course.copy(id="other",name="另一门课",day=4)
        assertEquals(listOf(next,other),applyLayoutChange(schedule.copy(courses=listOf(course,other)),schedule,course,next).courses)
        assertThrows(IllegalArgumentException::class.java) { applyLayoutChange(schedule.copy(courses=listOf(course.copy(notes="其他窗口修改"))),schedule,course,next) }
        assertThrows(IllegalArgumentException::class.java) { applyLayoutChange(schedule.copy(periods=schedule.periods.map { it.copy(label="已修改") }),schedule,course,next) }
        assertThrows(IllegalArgumentException::class.java) { applyLayoutChange(schedule,schedule,course,next.copy(name="不能改名")) }
    }
    @Test fun conflictApprovalMustStillMatchLatestCollidingCourses() {
        val other=course.copy(id="collision",name="冲突课",day=3)
        val current=schedule.copy(courses=listOf(course,other));val next=course.copy(day=3)
        val conflict=assertThrows(LayoutConflict::class.java) { applyLayoutChange(current,schedule,course,next) }
        assertEquals(listOf(next,other),applyLayoutChange(current,schedule,course,next,conflict.overlaps).courses)
        val changed=current.copy(courses=listOf(course,other.copy(room="教室已变更")))
        assertThrows(LayoutConflict::class.java) { applyLayoutChange(changed,schedule,course,next,conflict.overlaps) }
    }
}
