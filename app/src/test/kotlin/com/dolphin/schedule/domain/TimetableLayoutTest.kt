package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test

class TimetableLayoutTest {
    private fun course(id: String,start: Int,end: Int)=Course(id=id,name=id,day=1,start=start,end=end,weeks=listOf(1))
    @Test fun conflictsShareLanesOnlyWithinTheirConnectedGroup() {
        val placed=positionCourses(listOf(course("later",8,9),course("a",1,3),course("b",3,5),course("c",4,6)))
        assertEquals(2,placed.first { it.course.id=="a" }.lanes)
        assertEquals(2,placed.first { it.course.id=="c" }.lanes)
        assertEquals(1,placed.first { it.course.id=="later" }.lanes)
        placed.forEach { a -> placed.filter { it.course.id!=a.course.id && it.course.start<=a.course.end && it.course.end>=a.course.start }.forEach { b -> assertNotEquals(a.lane,b.lane) } }
    }
    @Test fun adjacentLessonsMayReuseOneLaneButSharedBoundaryMayNot() {
        assertTrue(positionCourses(listOf(course("a",1,2),course("b",3,4))).all { it.lanes==1 })
        assertTrue(positionCourses(listOf(course("a",1,2),course("b",2,4))).all { it.lanes==2 })
    }
    @Test fun realNoonLessonsStayLessonsAndOnlyActualGapsBecomeRest() {
        val periods=listOf(Period("11:00","11:45","4"),Period("11:55","12:40","中午1"),Period("12:50","13:35","中午2"),Period("14:00","14:45","5"))
        assertEquals(4,timetableRows(periods).size)
        assertEquals(RestTime("课间","11:45","11:55"),inferredRest(periods,0))
        assertEquals(RestTime("课间","13:35","14:00"),inferredRest(periods,2))
        assertNull(inferredRest(periods,3))
    }
    @Test fun explicitRestWinsAndLongBlankGapBecomesLunch() {
        val rest=RestTime("活动","09:40","09:55")
        assertEquals(rest,inferredRest(listOf(Period("09:00","09:40",breakAfter=rest),Period("10:00","10:45")),0))
        assertEquals("午休",inferredRest(listOf(Period("11:00","11:45"),Period("14:00","14:45")),0)?.label)
        assertEquals(0f,timetableRows(listOf(Period("08:00","08:45"))).single().gap)
    }
}
