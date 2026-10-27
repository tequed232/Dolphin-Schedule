package com.dolphin.schedule.data

import com.dolphin.schedule.domain.AcademicBlock
import com.dolphin.schedule.domain.AcademicRow
import com.dolphin.schedule.domain.AcademicSnapshot
import com.dolphin.schedule.domain.MAX_PERIODS
import org.json.JSONException
import org.json.JSONObject

object AcademicCodec {
    fun decode(raw: String): AcademicSnapshot=try { decodeChecked(raw) }
    catch(failure: JSONException) { throw IllegalArgumentException("教务结果缺少必要字段，请重新查询完整学期后识别",failure) }
    private fun integer(value: JSONObject,key: String): Int {
        val number=value.get(key)
        require(number is Number && number.toDouble()==number.toInt().toDouble()) { "教务周次或格式版本必须是整数" }
        return number.toInt()
    }
    private fun decodeChecked(raw: String): AcademicSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size<=700000) { "教务结果过大" }
        val value=AppDataCodec.parseObject(raw)
        require(integer(value,"schema")==1 && value.optString("school")=="gdcmxy") { "教务数据格式无效" }
        val rows=value.getJSONArray("rows");require(rows.length() in 1..MAX_PERIODS) { "逐节作息不完整" }
        var total=0
        return AcademicSnapshot(value.getString("term"),integer(value,"startWeek"),integer(value,"endWeek"),(0 until rows.length()).map { index ->
            val row=rows.getJSONObject(index);val cells=row.getJSONArray("cells");require(cells.length()==7) { "星期列缺失" }
            AcademicRow(row.getString("label"),row.getString("start"),row.getString("end"),(0 until cells.length()).map { day ->
                val blocks=cells.getJSONArray(day);require(blocks.length()<=100) { "同一时段的课程过多" }
                (0 until blocks.length()).map { n ->
                    require(++total<=2000) { "课程块过多" };val block=blocks.getJSONObject(n)
                    AcademicBlock(block.getString("name"),block.getString("weeks"),block.getString("teacher"),block.getString("room"))
                }
            })
        },if(value.isNull("termStartDate")) null else value.getString("termStartDate"))
    }
}
