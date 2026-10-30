package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test

class AcademicMergeTest {
    private val manual=Course(id="manual",name="手动课程",day=3,start=1,end=2,weeks=listOf(1,2))
    private val school=manual.copy(id="school",name="教务课程",day=1,source="academic:gdcmxy")
    private val base=defaultSchedule().copy(term=Term("2026-2027-1","2026-09-01",21),courses=listOf(manual,school))
    @Test fun identicalManualOccurrencesKeepTheirSeparateNotesAndExposeTheConflict() {
        val first=school.copy(id="first",source="manual",notes="第一份备注")
        val second=first.copy(id="second",notes="第二份备注")
        val result=academicMerge(base.copy(courses=listOf(first,second)),base.copy(courses=listOf(school)),true)
        assertEquals(listOf(first,second),result.schedule.courses);assertEquals(1,result.conflicts.size)
    }
    @Test fun deduplicatingASchoolOccurrenceAlsoResolvesItsManualIdCollisionElsewhere() {
        val another=school.copy(id="manual",name="其他教务课",day=6)
        val personal=school.copy(id="manual",source="manual",notes="保留")
        val result=academicMerge(base.copy(courses=listOf(personal)),base.copy(courses=listOf(school,another)),true)
        assertEquals(2,result.schedule.courses.size);assertEquals(2,result.schedule.courses.map { it.id }.distinct().size)
        assertEquals(personal,result.schedule.courses.first { it.source=="manual" })
    }
    @Test fun replacementIsStillAvailableAndKeepingOnlyProtectsUserControlledSources() {
        val next=base.copy(courses=listOf(school.copy(room="新地点")))
        assertEquals(next,academicMerge(base,next,false).schedule)
        val merged=academicMerge(base,next,true)
        assertEquals(listOf("school","manual"),merged.schedule.courses.map { it.id });assertEquals(1,merged.keptCount)
        assertTrue(merged.conflicts.isEmpty())
        assertEquals("新地点",merged.schedule.courses.first().room)
    }
    @Test fun fileAndUnknownSourcesAreProtectedWithoutGuessingFromNamesOrIdPrefixes() {
        val unknown=manual.copy(id="gdcmxy-not-proof",name=school.name,source="unknown")
        val file=manual.copy(id="file",day=4,source="file")
        val merged=academicMerge(base.copy(courses=listOf(unknown,file)),base.copy(courses=listOf(school)),true)
        assertEquals(2,merged.keptCount);assertTrue(merged.schedule.courses.containsAll(listOf(unknown,file)))
    }
    @Test fun removedNoonSlotRemapsByExactTimesAndNeverByArrayPosition() {
        val old=base.copy(periods=listOf(Period("12:10","12:50","中午1"),Period("14:10","14:50","5")),courses=listOf(manual.copy(start=2,end=2)))
        val next=old.copy(periods=old.periods.drop(1),courses=emptyList())
        assertEquals(1,academicMerge(old,next,true).schedule.courses.single().start)
        assertThrows(IllegalArgumentException::class.java) { academicMerge(old,next.copy(periods=listOf(Period("14:20","15:00","5"))),true) }
    }
    @Test fun changingTermOrUnusableTimesBlocksKeepingButNotExplicitReplacement() {
        val next=base.copy(term=base.term.copy(startDate="2026-09-07"),courses=listOf(school))
        assertThrows(IllegalArgumentException::class.java) { academicMerge(base,next,true) }
        assertEquals(next,academicMerge(base,next,false).schedule)
        val emptyTimes=base.copy(periods=base.periods.mapIndexed { i,p->if(i==0)p.copy(start="",end="") else p })
        assertThrows(IllegalArgumentException::class.java) { academicMerge(emptyTimes,base,true) }
    }
    @Test fun anIdenticalSchoolOccurrenceKeepsManualNotesColorAndIdOnce() {
        val custom=school.copy(id="custom",source="manual",notes="自己记录",color="rose")
        val merged=academicMerge(base.copy(courses=listOf(custom)),base.copy(courses=listOf(school)),true)
        assertEquals(listOf(custom),merged.schedule.courses);assertTrue(merged.conflicts.isEmpty())
    }
    @Test fun idCollisionWithDifferentContentKeepsBothAndReportsTheTimeConflict() {
        val edited=school.copy(source="manual",room="自己修正的教室")
        val merged=academicMerge(base.copy(courses=listOf(edited)),base.copy(courses=listOf(school)),true)
        assertEquals(2,merged.schedule.courses.size);assertEquals(2,merged.schedule.courses.map { it.id }.distinct().size)
        assertEquals(1,merged.conflicts.size);assertTrue(merged.schedule.courses.contains(edited))
    }
}
