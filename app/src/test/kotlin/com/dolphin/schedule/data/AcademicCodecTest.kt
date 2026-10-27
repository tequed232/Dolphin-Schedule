package com.dolphin.schedule.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AcademicCodecTest {
    private fun raw(): JSONObject=JSONObject().put("schema",1).put("school","gdcmxy").put("term","2026-2027-1").put("startWeek",1).put("endWeek",21)
        .put("rows",JSONArray().put(JSONObject().put("label","1").put("start","08:00").put("end","08:45").put("cells",JSONArray(List(7) { JSONArray() }))))
    @Test fun rawWeekNumbersAreNotSilentlyTruncatedOrCoerced() {
        listOf("schema","startWeek","endWeek").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { AcademicCodec.decode(raw().put(key,1.5).toString()) }
            assertThrows(IllegalArgumentException::class.java) { AcademicCodec.decode(raw().put(key,"1").toString()) }
        }
    }
    @Test fun missingFieldsAndInvalidJsonReportAnActionableChineseError() {
        val value=raw().apply { remove("rows") }
        val failure=assertThrows(IllegalArgumentException::class.java) { AcademicCodec.decode(value.toString()) }
        assertTrue(failure.message!!.contains("重新查询完整学期"))
        assertThrows(IllegalArgumentException::class.java) { AcademicCodec.decode("not json") }
    }
    @Test fun weekColumnsAndSchoolAreCheckedAndOptionalDateRemainsExplicit() {
        assertNull(AcademicCodec.decode(raw().toString()).termStartDate)
        assertEquals("2026-09-01",AcademicCodec.decode(raw().put("termStartDate","2026-09-01").toString()).termStartDate)
        assertThrows(IllegalArgumentException::class.java) { AcademicCodec.decode(raw().put("school","unknown").toString()) }
        val value=raw();value.getJSONArray("rows").getJSONObject(0).put("cells",JSONArray(List(6) { JSONArray() }))
        assertThrows(IllegalArgumentException::class.java) { AcademicCodec.decode(value.toString()) }
    }
}
