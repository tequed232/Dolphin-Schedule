package com.dolphin.schedule.data

import com.dolphin.schedule.domain.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AppDataCodecTest {
    private fun fixture(): AppData {
        val schedule=Schedule(Term("合成学期","2026-09-01",21),listOf(Period("12:10","12:50","中午1"),Period("14:10","14:50","5")),listOf(Course(id="stable-course",name="合成课程",day=4,start=2,end=2,weeks=listOf(1,3,5),teacher="示例教师",room="示例教室")))
        return AppData(schedule=schedule,books=mapOf("stable-course" to Book(title="合成教材",publisher="示例出版社",edition="第2版",text="本地备注")),settings=Preferences(mode="dark",scale=1.2f,school="示例学校"),onboarded=true,
            background=Background("data:image/png;base64,c3ludGhldGlj","合成图片",20,30,40,60),
            academicImport=AcademicHistory("gdcmxy",schedule.term.name,"2026-10-26T00:00:00Z",schedule.copy(courses=emptyList()),schedule))
    }
    @Test fun nativeRoundTripPreservesScheduleBooksPreferencesBackgroundAndUndo() {
        val data=fixture();assertEquals(data,AppDataCodec.decode(AppDataCodec.encode(data)))
    }
    @Test fun retiredPreferencesInOldBackupDoNotRestoreRemovedFeatures() {
        val original=fixture();val raw=AppDataCodec.parseObject(AppDataCodec.encode(original))
        raw.put("holidays",JSONArray().put("old cache"))
        raw.getJSONObject("settings").put("reminders",true).put("advance",15).put("pet",true).put("poke",true).put("lines","旧台词").put("holidayMarkers",true).put("autoUpdate",true)
        val value=AppDataCodec.decode(raw.toString())
        assertEquals(original,value)
        val encoded=AppDataCodec.parseObject(AppDataCodec.encode(value))
        assertFalse(encoded.has("holidays"))
        listOf("reminders","advance","pet","poke","lines","holidayMarkers","autoUpdate").forEach { assertFalse(encoded.getJSONObject("settings").has(it)) }
    }
    @Test fun oldSchemaOneCourseAliasesMigrateWithoutLosingBooksOrBackground() {
        val original=fixture();val raw=AppDataCodec.parseObject(AppDataCodec.encode(original))
        val course=raw.getJSONObject("schedule").getJSONArray("courses").getJSONObject(0)
        course.put("startSection",course.remove("start")).put("endSection",course.remove("end")).put("dayOfWeek",course.remove("day"))
        val settings=raw.getJSONObject("settings");settings.remove("timetableMode");settings.put("viewMode","grid")
        val migrated=AppDataCodec.decode(raw.toString())
        assertEquals(original.copy(settings=original.settings.copy(timetableMode="grid")),migrated)
        course.remove("startSection");course.remove("endSection");course.put("section",2)
        assertEquals(migrated,AppDataCodec.decode(raw.toString()))
        course.put("start",1).put("end",2).put("day",1)
        val canonical=AppDataCodec.decode(raw.toString()).schedule.courses.single()
        assertEquals(1,canonical.start);assertEquals(2,canonical.end);assertEquals(1,canonical.day)
    }
    @Test fun legacyEmptyWeeksAndHiddenCoursesAreKeptWithoutExpandingDates() {
        val original=fixture();val hidden=original.schedule.courses.single().copy(id="hidden",start=10,end=12,weeks=emptyList())
        val data=original.copy(schedule=original.schedule.copy(courses=listOf(hidden)))
        val decoded=AppDataCodec.decode(AppDataCodec.encode(data))
        assertEquals(hidden,decoded.schedule.courses.single());assertEquals(original.books,decoded.books)
    }
    @Test fun corruptPayloadAndDeepStructuresFailInsteadOfReturningAnEmptySchedule() {
        val raw=AppDataCodec.parseObject(AppDataCodec.encode(fixture()))
        raw.getJSONObject("schedule").getJSONArray("courses").getJSONObject(0).put("weeks",JSONArray().put("1"))
        assertThrows(IllegalArgumentException::class.java) { AppDataCodec.decode(raw.toString()) }
        assertThrows(Exception::class.java) { AppDataCodec.decode("{}") }
        assertThrows(IllegalArgumentException::class.java) { AppDataCodec.parseObject("[".repeat(65)+"0"+"]".repeat(65)) }
    }
    @Test fun invalidClockAndOverlappingRestCannotBeWritten() {
        val data=fixture()
        assertThrows(IllegalArgumentException::class.java) { AppDataCodec.encode(data.copy(schedule=data.schedule.copy(periods=listOf(Period("08:00:00","09:00"))))) }
        assertThrows(IllegalArgumentException::class.java) { AppDataCodec.encode(data.copy(schedule=data.schedule.copy(periods=listOf(Period("08:00","08:45",breakAfter=RestTime("课间","08:30","08:55")),Period("09:00","09:45"))))) }
    }
}
