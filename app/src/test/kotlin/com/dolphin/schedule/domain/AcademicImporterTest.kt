package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test

class AcademicImporterTest {
    private fun snapshot(): AcademicSnapshot {
        val times=listOf(Triple("1","08:30","09:10"),Triple("2","09:15","09:55"),Triple("中午1","12:10","12:50"),Triple("3","14:10","14:50"))
        val rows=times.map { (label,start,end) -> AcademicRow(label,start,end,List(7) { day -> if(day==3 && label in listOf("1","2")) listOf(AcademicBlock("合成课程","4;6-9;12-18","示例教师","示例教室")) else if(day==1 && label=="中午1") listOf(AcademicBlock("午间实践","1-21双","示例教师","--")) else emptyList() }) }
        return AcademicSnapshot("2026-2027-1",1,21,rows,"2026-09-01")
    }
    @Test fun countNamesNoonWeeksAndConsecutiveCourseMergingComeFromSource() {
        val result=AcademicImporter.convert(snapshot(),defaultSchedule())
        assertEquals(4,result.periods.size);assertEquals(listOf("1","2","中午1","3"),result.periods.map { it.label })
        assertEquals(2,result.courses.size);val joined=result.courses.first { it.name=="合成课程" }
        assertEquals(1,joined.start);assertEquals(2,joined.end);assertEquals(4,joined.day)
        assertEquals(listOf(4,6,7,8,9,12,13,14,15,16,17,18),joined.weeks)
        assertEquals("",result.courses.single { it.name=="午间实践" }.room)
    }
    @Test fun dateRequiresExplicitFallbackAndSameContentKeepsIdsAndColors() {
        val raw=snapshot();val first=AcademicImporter.convert(raw,defaultSchedule());val colored=first.copy(courses=first.courses.map { it.copy(color="rose") })
        val again=AcademicImporter.convert(raw,colored)
        assertEquals(colored.courses,again.courses)
        assertTrue(AcademicImporter.difference(colored,again).added.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { AcademicImporter.convert(raw.copy(termStartDate=null),first) }
        assertEquals("2026-09-07",AcademicImporter.convert(raw.copy(termStartDate=null),first,"2026-09-07").term.startDate)
    }
    @Test fun invalidOrEmptyPartialTimetablesNeverReplaceExistingData() {
        val raw=snapshot();val current=defaultSchedule()
        assertThrows(IllegalArgumentException::class.java) { AcademicImporter.convert(raw.copy(startWeek=3),current) }
        assertThrows(IllegalArgumentException::class.java) { AcademicImporter.convert(raw.copy(rows=raw.rows.filterNot { it.label=="2" }),current) }
        assertThrows(IllegalArgumentException::class.java) { AcademicImporter.convert(raw.copy(rows=raw.rows.map { it.copy(cells=List(7) { emptyList() }) }),current) }
        assertThrows(IllegalArgumentException::class.java) { AcademicImporter.convert(raw.copy(rows=raw.rows.mapIndexed { i,r -> if(i==1) r.copy(start="08:45") else r }),current) }
    }
    @Test fun applyAndUndoPreserveBooksPreferencesAndOriginalScheduleExactly() {
        val before=AppData(books=mapOf("stable" to Book(title="合成教材")),settings=Preferences(mode="dark"))
        val next=AcademicImporter.apply(before,AcademicImporter.convert(snapshot(),before.schedule))
        assertEquals(before.books,next.books);assertEquals(before.settings,next.settings)
        assertEquals(before.schedule,next.academicImport!!.before)
        val restored=AcademicImporter.undo(next,next.schedule)
        assertEquals(before.schedule,restored.schedule);assertEquals(before.books,restored.books);assertNull(restored.academicImport)
        assertThrows(IllegalArgumentException::class.java) { AcademicImporter.undo(next,next.schedule.copy(term=next.schedule.term.copy(name="修改过"))) }
    }
}
