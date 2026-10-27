package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class PeriodEditingTest {
    private fun schedule()=Schedule(Term("合成学期","2026-09-01",20),listOf(
        Period("11:00","11:40","4"),Period("12:10","12:50","中午1"),Period("12:55","13:35","中午2"),Period("14:10","14:50","5"),
    ),listOf(Course(id="afternoon",name="合成课程",day=1,start=4,end=4,weeks=listOf(1))))
    @Test fun unusedNoonPlaceholdersCollapseWithoutChangingSchoolIndices() {
        val s=schedule();assertEquals(listOf(0,3),visiblePeriodIndices(s))
        assertEquals(0f,displayTimetableRows(s)[1].height)
        assertEquals(4,s.courses.single().start)
        val occupied=s.copy(courses=s.courses+Course(id="noon",name="午间活动",day=2,start=2,end=3,weeks=listOf(1)))
        assertEquals(listOf(0,1,2,3),visiblePeriodIndices(occupied))
    }
    @Test fun deletingNoonSlotsRemapsCoursesWithoutChangingTheirTimesOrIdentity() {
        val initial=schedule();val revised=removePeriod(removePeriod(initial,2),1)
        assertEquals(2,revised.periods.size);assertEquals(2,revised.courses.single().start)
        assertEquals(initial.periods[3],revised.periods[1]);assertEquals(initial.courses.single().id,revised.courses.single().id)
        assertThrows(IllegalArgumentException::class.java) { removePeriod(initial,3) }
        assertThrows(IllegalArgumentException::class.java) { removePeriod(revised.copy(periods=revised.periods.take(1),courses=emptyList()),0) }
    }
    @Test fun clockLineUsesLessonProgressAndInterpolatesCollapsedLunch() {
        val s=schedule();val rows=displayTimetableRows(s)
        assertEquals(rows[0].lesson/2,timetableTimeOffset(s.periods,rows,LocalTime.of(11,20))!!,.001f)
        val lunch=timetableTimeOffset(s.periods,rows,LocalTime.of(12,55))!!
        assertEquals(rows[0].lesson+rows[0].gap/2,lunch,.001f)
        assertNull(timetableTimeOffset(s.periods,rows,LocalTime.of(8,0)))
        assertNull(timetableTimeOffset(s.periods,rows,LocalTime.of(18,0)))
    }
    @Test fun unknownTimesDoNotInventAnIndicator() {
        val periods=listOf(Period("",""))
        assertNull(timetableTimeOffset(periods,timetableRows(periods),LocalTime.NOON))
    }
}
