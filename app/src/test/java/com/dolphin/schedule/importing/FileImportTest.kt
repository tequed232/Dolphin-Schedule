package com.dolphin.schedule.importing

import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FileImportTest {
    private val current=defaultSchedule().copy(term=Term("原学期","2026-09-01",20),courses=listOf(Course(id="保留-ID",name="保留课程",day=1,start=1,end=2,weeks=listOf(1,3,7))))
    private fun csv(raw: String)=TableImport.parseSheet(ImportSheet("课表",TableImport.csv(raw)),current)
    private fun fails(block: ()->Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun jsonWrappersChineseAliasesAndOddEvenWeeksAreNormalized() {
        val result=FileNormalizer.json("""说明：```json
            {"data":[{"课程名称":"高等数学","星期":"周三","节次":"3-4","周次":"1-19(单)","教师":"陈老师","教室":"16-203"}]} 后续说明
        """.trimIndent(),current).schedule
        val c=result.courses.single();assertEquals("高等数学",c.name);assertEquals(3,c.day);assertEquals(3,c.start);assertEquals(4,c.end);assertEquals((1..19 step 2).toList(),c.weeks);assertEquals("16栋203号教室",c.room)
        assertEquals(current.term.startDate,result.term.startDate)
    }
    @Test fun explicitWeeksExpandTermAndMissingWeeksUseTheWholeDetectedTerm() {
        val result=FileNormalizer.json("""{"courses":[{"name":"全年课程","day":1,"start":1,"weeks":"1-38"},{"name":"默认周次","day":2,"start":2}]}""",current).schedule
        assertEquals(38,result.term.weeks);assertEquals(38,result.courses.last().weeks.size)
    }
    @Test fun appBackupPreservesIdsHiddenPeriodsEmptyWeeksAndExactRoomNotes() {
        val original=current.copy(term=current.term.copy(weeks=3),periods=current.periods.take(2),courses=listOf(current.courses.single().copy(id="全角ＩＤ",room="16-203",end=24,weeks=emptyList(),notes="原始备注")))
        val restored=FileNormalizer.json(AppDataCodec.encodeSchedule(original).toString(),current).schedule
        assertEquals(original.courses,restored.courses);assertEquals(original.term,restored.term);assertEquals(original.periods,restored.periods)
    }
    @Test fun configOnlyJsonRetainsCoursesAndCanProvideNewClockSettings() {
        val result=FileNormalizer.json("""{"term":{"name":"新学期"},"courseCount":4,"firstStart":"8:15","duration":40,"breakMinutes":5}""",current)
        assertTrue(result.onlyConfiguration);assertEquals(current.courses,result.schedule.courses);assertEquals(4,result.schedule.periods.size)
        assertEquals(Period("08:15","08:55"),result.schedule.periods.first());assertEquals("11:10",result.schedule.periods.last().end)
    }
    @Test fun malformedCourseRowsAreReportedRatherThanSilentlyLost() {
        val result=FileNormalizer.json("""{"courses":[{"name":"有效课程","day":1,"start":1},{"name":"错误课程","day":8,"start":2}]}""",current)
        assertEquals(1,result.schedule.courses.size);assertEquals(1,result.errors.size);assertTrue(result.errors.single().contains("第 2 条"))
        fails { FileNormalizer.json("""{"courses":[{"name":"错误课程","day":8,"start":2}]}""",current) }
        fails { FileNormalizer.json("""{"courses":[]}""",current) }
    }
    @Test fun duplicateIdsBadTimesAndHugeCourseCountsCannotPassPartialRecovery() {
        val backup=AppDataCodec.encodeSchedule(current.copy(courses=listOf(current.courses.single(),current.courses.single())))
        fails { FileNormalizer.json(backup.toString(),current) }
        fails { FileNormalizer.json("""{"times":[{"start":"08:00","end":"07:45"}],"courses":[{"name":"课程","day":1,"start":1}]}""",current) }
        val courses=(0..21).map { mapOf("name" to "课程$it","day" to 1,"start" to 1,"weeks" to "1-999") }
        fails { FileNormalizer.json(JSONObject(mapOf("courses" to courses)).toString(),current) }
    }
    @Test fun groupedMatrixKeepsNoonGroupsAndSupportsMultipleCoursesInOneCell() {
        val result=FileNormalizer.json("""{"periods":[{"period":"1-2","days":["普通课"]},{"period":"中午1","days":[null,[{"name":"午间甲","weeks":"单周"},{"name":"午间乙","weeks":"双周"}]]}]}""",current).schedule
        assertEquals(3,result.courses.size);assertEquals("中午1",result.periods[1].label);assertEquals("",result.periods[1].start);assertEquals(2,result.courses.last().start)
    }
    @Test fun csvQuotesNewlinesBomAndGb18030AreDecoded() {
        val rows=TableImport.csv("课程名,备注\r\n\"数学,甲\",\"两行\n及\"\"引号\"\"\"\r\n")
        assertEquals("数学,甲",rows[1][0]);assertEquals("两行\n及\"引号\"",rows[1][1])
        assertEquals("课程名",TableImport.decodeText("\uFEFF课程名".toByteArray(Charsets.UTF_8)))
        assertEquals("课程名",TableImport.decodeText("课程名".toByteArray(charset("GB18030"))))
        assertEquals("课程名",TableImport.decodeText(byteArrayOf(-1,-2)+"课程名".toByteArray(Charsets.UTF_16LE)))
        fails { TableImport.csv("a,b\n\"unterminated") }
        fails { TableImport.csv((0..100).joinToString(",") { "列" }) }
    }
    @Test fun mismatchedExtensionRequiresExplicitFormatChoice() {
        val failure=assertThrows(FormatMismatch::class.java) { TableImport.detect("课表.json","课程名,星期,节次\n数学,周一,1".toByteArray()) }
        assertEquals(ImportFormat.CSV,failure.suggested)
        assertEquals(ImportFormat.JSON,TableImport.detect("unknown","[{\"name\":\"课程\"}]".toByteArray()))
        fails { TableImport.detect("empty.csv",ByteArray(0)) }
    }
    @Test fun headerCsvParsesChineseSectionsAndReportsBadCourses() {
        val result=csv("课程名,星期,节次,周次,教室\n数学,Wednesday,第一节,1-16,16-203\n错误,8,2,1-16,A101\n")
        assertEquals(1,result.errors.size);assertEquals(1,result.schedule.courses.size);assertEquals(3,result.schedule.courses.single().day)
        assertEquals("16栋203号教室",result.schedule.courses.single().room)
    }
    @Test fun completeSingleLessonTimesReplaceCountButKeepCoursesAndIds() {
        val result=csv("节次,上课时间,下课时间\n1,8:00,8:40\n2,8:50,9:30\n午休,9:30,10:30\n3,10:30,11:10\n")
        assertTrue(result.onlyConfiguration);assertEquals(3,result.schedule.periods.size);assertEquals(current.courses,result.schedule.courses)
        assertEquals(RestTime("午休","09:30","10:30"),result.schedule.periods[1].breakAfter)
    }
    @Test fun partialBoundaryUpdatesNeverAverageTheMiddleOfAConsecutiveClass() {
        val result=csv("课程名,星期,节次,上课时间,下课时间\n连堂课,周一,1-3,07:55,10:50\n")
        assertEquals("07:55",result.schedule.periods[0].start);assertEquals(current.periods[1],result.schedule.periods[1]);assertEquals("10:50",result.schedule.periods[2].end)
        assertTrue(result.schedule.importNotes.any { it.contains("未作推算") })
    }
    @Test fun conflictingOrPartialTimesAreFatalEvenIfOtherCoursesAreValid() {
        fails { csv("课程名,星期,节次,上课时间,下课时间\n甲,周一,1,08:00,08:45\n乙,周二,1,08:10,08:45") }
        fails { csv("课程名,星期,节次,上课时间,下课时间\n甲,周一,1,08:00,") }
        fails { csv("节次,上课时间,下课时间\n1-2,08:00,09:40") }
        fails { csv("课程名,星期,节次,上课时间,下课时间\n甲,周一,14,22:00,22:30") }
    }
    @Test fun sheetMergesAreCountedOnceAndManualMappingCanOverrideAutomaticDetection() {
        val sheet=ImportSheet("网格",listOf(listOf("节次","周一","周二"),listOf("1","课程\n教师：张老师\n周次：1-16",""),listOf("2","","")),listOf(CellMerge(1,1,2,1)))
        val guessed=TableImport.mapping(sheet);assertEquals(0,guessed.dayRow);assertEquals(0,guessed.sectionColumn)
        val result=TableImport.parseSheet(sheet,current,guessed).schedule
        assertEquals(1,result.courses.size);assertEquals(2,result.courses.single().end);assertEquals("张老师",result.courses.single().teacher)
        assertEquals(result.courses.single().name,TableImport.parseSheet(sheet,current,guessed.copy(lastRow=1)).schedule.courses.single().name)
        fails { TableImport.parseSheet(sheet,current,guessed.copy(lastRow=99)) }
    }
    @Test fun horizontalDayMergesProduceErrorsAndCannotEraseAllCourses() {
        val sheet=ImportSheet("网格",listOf(listOf("节次","周一","周二"),listOf("1","课程","")),listOf(CellMerge(1,1,1,2)))
        fails { TableImport.parseSheet(sheet,current) }
    }
    @Test fun singleCompleteTimeTableCannotHideNewlyImportedCourseOutsideItsCount() {
        fails { csv("课程名,星期,节次,上课时间,下课时间\n,,1,08:00,08:45\n,,2,08:55,09:40\n课程,周一,4,,") }
    }
}
