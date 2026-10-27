package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    private fun schedule(): Schedule = Schedule(Term("测试学期","2026-09-01",21),listOf(Period("08:30","09:10","1"),Period("09:15","09:55","2")))
    @Test fun semesterDateAnchorsToMondayAndSpecificDatesOverrideWeeks() {
        val course=Course(id="normal",name="合成课程",day=4,start=1,end=2,weeks=listOf(1,3))
        val temporary=course.copy(id="temporary",specificDate="2026-09-04",weeks=emptyList())
        val data=schedule().copy(courses=listOf(course,temporary))
        assertEquals("2026-08-31",firstMonday(data.term.startDate).toString())
        assertEquals(1,weekOf(LocalDate.parse("2026-09-03"),data.term.startDate))
        assertEquals(listOf(course),coursesOn(data,LocalDate.parse("2026-09-03")))
        assertEquals(listOf(temporary),coursesOn(data,LocalDate.parse("2026-09-04")))
        assertTrue(coursesOn(data,LocalDate.parse("2026-09-10")).isEmpty())
        assertEquals(listOf(LocalDate.parse("2026-09-03"),LocalDate.parse("2026-09-17")),courseDates(course,data))
    }
    @Test fun weekSegmentsKeepTheirOwnParityAndRejectOutOfRangeValues() {
        assertEquals(listOf(1,3,5,8,10,12),parseWeeks("1-6单;8-12双",21))
        assertEquals(listOf(4,6,7,8,9,12,13,14,15,16,17,18),parseWeeks("4;6-9;12-18",21))
        assertEquals("1-3;5;8-9",compactWeeks(listOf(3,2,1,5,8,9,9)))
        assertThrows(IllegalArgumentException::class.java) { parseWeeks("1-22",21) }
    }
    @Test fun labelsAndActualPeriodCountArePreservedWithoutDroppingMidday() {
        val data=schedule().copy(periods=listOf(Period("12:10","12:50","中午1"),Period("14:10","14:50","5")))
        assertEquals(data,validateSchedule(data))
        assertEquals("中午1",periodLabel(data.periods[0],0))
        assertEquals("第 5 节",coursePeriod(Course(name="课程",day=1,start=2,end=2,weeks=listOf(1)),data))
        assertThrows(IllegalArgumentException::class.java) { validateSchedule(data.copy(periods=listOf(Period("14:00","14:45"),Period("14:30","15:10")))) }
    }
    @Test fun conflictsCompareRecurringAndSpecificDatesWithoutMutatingCourses() {
        val a=Course(id="a",name="甲",day=4,start=1,end=2,weeks=listOf(1))
        val b=a.copy(id="b",name="乙",weeks=emptyList(),specificDate="2026-09-03")
        assertTrue(coursesConflict(a,b,schedule()))
        assertFalse(coursesConflict(a,b.copy(specificDate="2026-09-10"),schedule()))
        assertFalse(coursesConflict(a,a,schedule()))
    }
}
