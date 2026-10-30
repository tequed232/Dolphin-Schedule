package com.dolphin.schedule.data

import com.dolphin.schedule.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AcademicPendingCodecTest {
    private val raw="""{"schema":1,"school":"gdcmxy","term":"2026-2027-1","termStartDate":"2026-09-01","startWeek":1,"endWeek":21,"rows":[{"label":"1","start":"08:00","end":"08:45","cells":[[{"name":"合成课程","weeks":"1-21","teacher":"教师","room":"1栋101"}],[],[],[],[],[],[]]}]}"""
    private val schedule=AcademicImporter.convert(AcademicCodec.decode(raw),defaultSchedule())
    private val pending=PendingAcademicUpdate("e920b162-6d70-4d46-a09a-1a7bd532927e",raw,schedule,"2026-10-30T04:00:00Z","2026-09-01")
    @Test fun pendingBaselineRawAndDatePersistWithoutChangingTheCommittedTimetable() {
        val data=AppData(schedule=schedule,pendingAcademic=pending,academicCheck=AcademicCheckState("pending",pending.receivedAt,"待核对"),settings=Preferences(academicCheckHours=6,keepManualOnAcademic=true))
        val result=AppDataCodec.decode(AppDataCodec.encode(data))
        assertEquals(data.schedule,result.schedule);assertEquals(pending.baseline,result.pendingAcademic!!.baseline)
        assertEquals(AcademicCodec.decode(raw),AcademicCodec.decode(result.pendingAcademic!!.raw));assertEquals(pending.confirmedDate,result.pendingAcademic!!.confirmedDate)
        assertEquals(data.settings,result.settings);assertEquals(data.academicCheck,result.academicCheck)
    }
    @Test fun exportedBackupExcludesPendingAndDoesNotEnableChecksOnAnotherDevice() {
        val data=AppData(schedule=schedule,pendingAcademic=pending,settings=Preferences(academicCheckHours=24))
        val backup=AppDataCodec.decode(AppDataCodec.encode(data,includeTransient=false))
        assertNull(backup.pendingAcademic);assertEquals(0,backup.settings.academicCheckHours);assertEquals(data.schedule,backup.schedule)
    }
    @Test fun oldSourcesUseExactRecordedIdsAndContentInsteadOfNames() {
        val user=schedule.courses.single().copy(id="user",source="manual",room="手动教室")
        val data=AppData(schedule=schedule.copy(courses=schedule.courses+user),academicImport=AcademicHistory("gdcmxy",schedule.term.name,pending.receivedAt,schedule,schedule))
        val value=JSONObject(AppDataCodec.encode(data))
        value.getJSONObject("schedule").getJSONArray("courses").let { rows->for(i in 0 until rows.length())rows.getJSONObject(i).remove("source") }
        value.getJSONObject("academicImport").getJSONObject("after").getJSONArray("courses").getJSONObject(0).remove("source")
        val result=AppDataCodec.decode(value.toString())
        assertEquals("academic:gdcmxy",result.schedule.courses.first().source);assertEquals("unknown",result.schedule.courses.last().source)
    }
    @Test fun malformedPendingCannotBeSilentlyAppliedOrTurnIntoAnEmptySchedule() {
        val value=JSONObject(AppDataCodec.encode(AppData(schedule=schedule,pendingAcademic=pending)))
        value.getJSONObject("pendingAcademic").put("id","invalid")
        assertThrows(IllegalArgumentException::class.java) { AppDataCodec.decode(value.toString()) }
        assertEquals(1,schedule.courses.size)
    }
}
