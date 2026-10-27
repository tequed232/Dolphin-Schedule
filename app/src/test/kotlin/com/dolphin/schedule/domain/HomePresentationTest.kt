package com.dolphin.schedule.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class HomePresentationTest {
    private val monday=LocalDate.of(2026,10,26)
    private val schedule=Schedule(Term("秋季",monday.toString(),20),listOf(Period("08:30","09:10"),Period("09:15","09:55"),Period("15:55","16:35")),listOf(
        Course(id="early",name="早课",day=1,start=1,end=2,weeks=listOf(1)),
        Course(id="late",name="晚课",day=1,start=3,end=3,weeks=listOf(1)),
    ))
    @Test fun dateStripRoundTripsAcrossLeapDaysAndBounds() {
        for(weekends in listOf(true,false)) {
            val range=DateStripRange(weekends)
            for(index in listOf(0,1,4,5,6,7,range.size/2,range.size-1))assertEquals(index,range.indexOf(range.dateAt(index)))
            val leap=LocalDate.of(2024,2,29);assertEquals(leap,range.dateAt(range.indexOf(leap)))
            assertTrue(range.dateAt(range.size-1)<=LocalDate.of(9999,12,31))
        }
    }
    @Test fun hiddenWeekendsJumpFromFridayToMonday() {
        val range=DateStripRange(false);val friday=LocalDate.of(2026,10,30)
        assertEquals(friday.plusDays(3),range.dateAt(range.indexOf(friday)+1))
        assertThrows(IllegalArgumentException::class.java) { range.indexOf(friday.plusDays(1)) }
    }
    @Test fun nextCourseUsesRealEndTimeAndBecomesCurrentAtStart() {
        assertEquals("下一节课",homeCourse(schedule,monday,LocalDateTime.parse("2026-10-26T08:29:59"))?.title)
        assertEquals("正在上课",homeCourse(schedule,monday,LocalDateTime.parse("2026-10-26T08:30:00"))?.title)
        assertEquals("early",homeCourse(schedule,monday,LocalDateTime.parse("2026-10-26T09:54:59"))?.course?.id)
        assertEquals("late",homeCourse(schedule,monday,LocalDateTime.parse("2026-10-26T09:55:00"))?.course?.id)
        assertNull(homeCourse(schedule,monday,LocalDateTime.parse("2026-10-26T16:35:00")))
    }
    @Test fun pastDateDoesNotShowFutureHeroAndSpecificDateDoes() {
        assertNull(homeCourse(schedule,monday,LocalDateTime.parse("2026-10-27T07:00:00")))
        assertEquals("当天第一节",homeCourse(schedule,monday,LocalDateTime.parse("2026-10-25T23:59:00"))?.title)
        val special=schedule.copy(courses=listOf(schedule.courses[0].copy(specificDate="2026-10-27",temporary=true)))
        assertNotNull(homeCourse(special,monday.plusDays(1),LocalDateTime.parse("2026-10-27T07:00:00")))
    }
    @Test fun blankTimesDoNotInventCurrentCourse() {
        val blank=schedule.copy(periods=schedule.periods.map { it.copy(start="",end="") })
        assertNull(homeCourse(blank,monday,LocalDateTime.parse("2026-10-26T07:00:00")))
        assertNotNull(homeCourse(blank,monday,LocalDateTime.parse("2026-10-25T07:00:00")))
    }
}
