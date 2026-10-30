package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test

class ScheduleDifferenceTest {
    private val course=Course(id="stable",name="课程甲",teacher="教师甲",room="1栋101",day=1,start=1,end=2,weeks=listOf(1,3))
    private val before=defaultSchedule().copy(term=Term("2026-2027-1","2026-09-01",20),courses=listOf(course))
    @Test fun sameContentWithNewIdsAndReorderedWeeksIsUnchangedWithoutMutatingInputs() {
        val after=before.copy(courses=listOf(course.copy(id="new-id",weeks=listOf(3,1,1))))
        val diff=compareSchedules(before,after)
        assertFalse(diff.hasChanges);assertEquals(1,diff.unchangedCount)
        assertEquals(listOf(1,3),before.courses.single().weeks);assertEquals(listOf(3,1,1),after.courses.single().weeks)
    }
    @Test fun roomTeacherAndWeekChangesWithContentBasedIdsAreOneModification() {
        val next=course.copy(id="new-content-id",room="2栋201",teacher="教师乙",weeks=listOf(2,4))
        val diff=compareSchedules(before,before.copy(courses=listOf(next)))
        assertTrue(diff.added.isEmpty());assertTrue(diff.removed.isEmpty())
        assertEquals(CourseModification(course,next),diff.modified.single())
        assertEquals(listOf("教师","地点","周次"),courseDifferenceFields(diff.modified.single()).map { it.label })
    }
    @Test fun uniqueNameCanExplainMovedCourseButAmbiguousNamesAreNotGuessed() {
        val moved=course.copy(id="moved",day=3,start=3,end=4)
        assertEquals(1,compareSchedules(before,before.copy(courses=listOf(moved))).modified.size)
        val second=course.copy(id="second",day=2)
        val old=before.copy(courses=listOf(course,second))
        val after=before.copy(courses=listOf(moved,second.copy(id="fourth",day=4)))
        val diff=compareSchedules(old,after)
        assertTrue(diff.modified.isEmpty());assertEquals(2,diff.added.size);assertEquals(2,diff.removed.size)
    }
    @Test fun uniqueStableIdCanExplainRenameWithoutMatchingDifferentNamesByTimeSlot() {
        val renamed=course.copy(name="新课程名")
        assertEquals(listOf("课程名"),courseDifferenceFields(compareSchedules(before,before.copy(courses=listOf(renamed))).modified.single()).map { it.label })
        val replacement=renamed.copy(id="unrelated")
        val diff=compareSchedules(before,before.copy(courses=listOf(replacement)))
        assertTrue(diff.modified.isEmpty());assertEquals(listOf(replacement),diff.added);assertEquals(listOf(course),diff.removed)
    }
    @Test fun sameNamedCoursesInDifferentTermsAreNotPairedByName() {
        val after=before.copy(term=before.term.copy(name="2026-2027-2"),courses=listOf(course.copy(id="new-term",room="2栋201")))
        val diff=compareSchedules(before,after)
        assertTrue(diff.termChanged);assertTrue(diff.modified.isEmpty());assertEquals(1,diff.added.size);assertEquals(1,diff.removed.size)
    }
    @Test fun duplicateContentIsCountedAsAMultisetInsteadOfDisappearing() {
        val old=before.copy(courses=listOf(course,course.copy(id="duplicate")))
        val diff=compareSchedules(old,before)
        assertEquals(1,diff.unchangedCount);assertEquals(1,diff.removed.size);assertTrue(diff.modified.isEmpty())
    }
    @Test fun datesWeekCountPeriodLabelsTimesBreaksAndDeletionAreAllReported() {
        val old=before.copy(periods=listOf(Period("08:00","08:45","1"),Period("08:55","09:40","2")))
        val after=old.copy(term=old.term.copy(startDate="2026-09-07",weeks=21),periods=listOf(Period("08:10","08:50","上午1",RestTime("课间","08:50","09:00"))))
        val diff=compareSchedules(old,after)
        assertTrue(diff.dateChanged);assertTrue(diff.weeksChanged);assertTrue(diff.timesChanged);assertEquals(2,diff.periodChanges.size)
        assertNull(diff.periodChanges.last().after);assertEquals(1,diff.unchangedCount)
        val added=compareSchedules(after,old)
        assertNull(added.periodChanges.last().before)
    }
    @Test fun manualNotesTemporaryFlagAndColorCannotBeSilentlyHidden() {
        val change=CourseModification(course.copy(notes="原备注",temporary=true,color="rose"),course)
        val diff=compareSchedules(before.copy(courses=listOf(change.before)),before)
        assertEquals(listOf("临时课程","备注","颜色"),courseDifferenceFields(diff.modified.single()).map { it.label })
        assertEquals("未填写",courseDifferenceFields(change).single { it.label=="备注" }.after)
    }
    @Test fun exactUnchangedOccurrencesAreRemovedBeforeMatchingTheRemainingUniqueName() {
        val second=course.copy(id="second",day=2)
        val next=second.copy(id="changed",day=3,room="3栋301")
        val diff=compareSchedules(before.copy(courses=listOf(course,second)),before.copy(courses=listOf(course.copy(id="same"),next)))
        assertEquals(1,diff.unchangedCount);assertEquals(CourseModification(second,next),diff.modified.single())
    }
    @Test fun courseTimeDetailUsesSchoolLabelsAndKeepsTheActualTimeAfterNoonSlotsAreRemoved() {
        val old=before.copy(periods=listOf(Period("12:10","12:50","中午1"),Period("14:10","14:50","5")),
            courses=listOf(course.copy(start=2,end=2)))
        val next=old.copy(periods=old.periods.drop(1),courses=listOf(old.courses.single().copy(start=1,end=1)))
        val modification=compareSchedules(old,next).modified.single()
        assertTrue(courseDifferenceFields(modification,old,next).isEmpty())
        val later=next.copy(periods=listOf(Period("14:20","15:00","5")))
        val time=courseDifferenceFields(modification,old,later).single()
        assertEquals("第 5 节 · 14:10—14:50",time.before);assertEquals("第 5 节 · 14:20—15:00",time.after)
    }
}
