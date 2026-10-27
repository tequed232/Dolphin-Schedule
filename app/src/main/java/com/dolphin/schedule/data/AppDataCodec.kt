package com.dolphin.schedule.data

import com.dolphin.schedule.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** 原生持久化和旧数据迁移共用的格式；课程结构不依赖 WebView。 */
object AppDataCodec {
    const val MAX_BYTES=16*1024*1024
    fun parseObject(raw: String): JSONObject {
        require(raw.length<=MAX_BYTES) { "数据文件超过 16 MB" }
        var depth=0;var quoted=false;var escaped=false
        raw.forEach { c ->
            if(quoted) { if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false }
            else when(c) { '"'->quoted=true;'[','{'->{depth++;require(depth<=64) { "数据嵌套过深" }};']','}'->depth-- }
        }
        return JSONObject(raw)
    }
    fun decode(raw: String): AppData {
        val value=parseObject(raw)
        require(value.optInt("schema",1)==1) { "无法识别这个备份版本" }
        val schedule=decodeSchedule(value.getJSONObject("schedule"))
        val books=value.optJSONObject("books") ?: JSONObject()
        require(books.length()<=10000) { "教材数量过多" }
        val history=value.optJSONObject("academicImport")?.let { AcademicHistory(it.getString("school"),it.getString("term"),it.getString("importedAt"),decodeSchedule(it.getJSONObject("before")),decodeSchedule(it.getJSONObject("after"))) }
        return AppData(
            schedule=schedule,books=books.keys().asSequence().associateWith { id -> books.getJSONObject(id).let { Book(it.text("title"),it.text("publisher"),it.text("edition"),it.optionalText("cover"),it.text("text"),it.text("source","manual")) } },
            settings=decodePreferences(value.optJSONObject("settings") ?: JSONObject()),onboarded=value.optBoolean("onboarded",false),
            background=value.optJSONObject("background")?.let { Background(it.getString("url"),it.text("name"),it.getInt("width"),it.getInt("height"),it.optInt("sourceWidth",it.getInt("width")),it.optInt("sourceHeight",it.getInt("height"))) },
            academicImport=history,
        )
    }
    fun encode(value: AppData): String {
        validateSchedule(value.schedule)
        val books=JSONObject();value.books.forEach { (id,b) -> books.put(id,JSONObject().put("title",b.title).put("publisher",b.publisher).put("edition",b.edition).put("cover",b.cover).put("text",b.text).put("source",b.source)) }
        val result=JSONObject().put("schema",1).put("appearanceRevision",2).put("schedule",encodeSchedule(value.schedule)).put("books",books)
            .put("settings",encodePreferences(value.settings)).put("onboarded",value.onboarded)
        value.background?.let { result.put("background",JSONObject().put("url",it.url).put("name",it.name).put("width",it.width).put("height",it.height).put("sourceWidth",it.sourceWidth).put("sourceHeight",it.sourceHeight)) }
        value.academicImport?.let { result.put("academicImport",JSONObject().put("school",it.school).put("term",it.term).put("importedAt",it.importedAt).put("before",encodeSchedule(it.before)).put("after",encodeSchedule(it.after))) }
        return result.toString().also { require(it.toByteArray(Charsets.UTF_8).size<=MAX_BYTES) { "本地数据超过 16 MB，请先精简封面图片" } }
    }
    fun decodeSchedule(value: JSONObject): Schedule {
        val term=value.getJSONObject("term");val rows=value.getJSONArray("periods");val courses=value.getJSONArray("courses")
        require(rows.length() in 1..MAX_PERIODS && courses.length()<=10000) { "课程节数或数量无效" }
        return validateSchedule(Schedule(
            Term(term.getString("name"),term.getString("startDate"),term.getInt("weeks")),
            rows.objects().map { p -> Period(p.text("start"),p.text("end"),p.optionalText("label"),p.optJSONObject("breakAfter")?.let { RestTime(it.getString("label"),it.getString("start"),it.getString("end")) }) },
            courses.objects().map { c ->
                // 与旧网页的 schema-1 迁移保持一致，未再次保存的旧课表也能直接升级。
                val start=c.firstInteger(1,"start","startSection","section")
                Course(c.getString("id"),c.getString("name"),c.text("teacher"),c.text("room"),c.firstInteger(1,"day","dayOfWeek"),start,c.firstInteger(start,"end","endSection","section"),c.getJSONArray("weeks").integers(),c.text("color","sage"),c.text("notes"),c.optBoolean("temporary",false),c.optionalText("specificDate"))
            },
            value.optJSONArray("importNotes").strings(),
        ))
    }
    fun encodeSchedule(value: Schedule): JSONObject = JSONObject()
        .put("term",JSONObject().put("name",value.term.name).put("startDate",value.term.startDate).put("weeks",value.term.weeks))
        .put("periods",JSONArray(value.periods.map { p -> JSONObject().put("start",p.start).put("end",p.end).put("label",p.label).also { obj -> p.breakAfter?.let { obj.put("breakAfter",JSONObject().put("label",it.label).put("start",it.start).put("end",it.end)) } } }))
        .put("courses",JSONArray(value.courses.map { c -> JSONObject().put("id",c.id).put("name",c.name).put("teacher",c.teacher).put("room",c.room).put("day",c.day).put("start",c.start).put("end",c.end).put("weeks",JSONArray(c.weeks)).put("color",c.color).put("notes",c.notes).put("temporary",c.temporary).put("specificDate",c.specificDate) }))
        .put("importNotes",JSONArray(value.importNotes))

    fun decodePreferences(s: JSONObject): Preferences {
        val d=Preferences()
        return Preferences(
            timetableMode=s.text("timetableMode",if(s.text("viewMode")=="grid")"grid" else d.timetableMode),
            showTimes=s.optBoolean("showTimes",d.showTimes),showCurrentTimeLine=s.optBoolean("showCurrentTimeLine",d.showCurrentTimeLine),mode=s.text("mode",d.mode).takeIf { it in listOf("system","light","dark") } ?: d.mode,scale=s.optDouble("scale",d.scale.toDouble()).toFloat().takeIf { it.isFinite() && it in .7f..1.5f } ?: 1f,
            showNavigation=s.optBoolean("showNavigation",d.showNavigation),showWeekend=s.optBoolean("showWeekend",d.showWeekend),
            topAuto=s.optBoolean("topAuto",d.topAuto),bottomAuto=s.optBoolean("bottomAuto",d.bottomAuto),topInset=s.optInt("topInset",d.topInset).coerceIn(0,100),bottomInset=s.optInt("bottomInset",d.bottomInset).coerceIn(0,100),
            backgroundEnabled=s.optBoolean("backgroundEnabled",d.backgroundEnabled),backgroundBlur=s.optInt("backgroundBlur",d.backgroundBlur).coerceIn(0,30),performance=s.text("performance",d.performance).takeIf { it in listOf("auto","high") } ?: d.performance,
            notificationsEnabled=s.optBoolean("notificationsEnabled",d.notificationsEnabled),journeyLive=s.optBoolean("journeyLive",d.journeyLive),school=s.text("school",d.school),map=s.text("map",d.map),dynamicColor=s.optBoolean("dynamicColor",d.dynamicColor),
        )
    }
    fun encodePreferences(s: Preferences): JSONObject=JSONObject()
        .put("timetableMode",s.timetableMode)
        .put("showTimes",s.showTimes).put("showCurrentTimeLine",s.showCurrentTimeLine).put("mode",s.mode).put("scale",s.scale)
        .put("showNavigation",s.showNavigation).put("showWeekend",s.showWeekend)
        .put("topAuto",s.topAuto).put("bottomAuto",s.bottomAuto).put("topInset",s.topInset).put("bottomInset",s.bottomInset)
        .put("backgroundEnabled",s.backgroundEnabled).put("backgroundBlur",s.backgroundBlur).put("performance",s.performance)
        .put("notificationsEnabled",s.notificationsEnabled).put("journeyLive",s.journeyLive).put("school",s.school).put("map",s.map).put("dynamicColor",s.dynamicColor)

    private fun JSONObject.text(key: String,fallback: String=""): String=if(isNull(key)) fallback else getString(key)
    private fun JSONObject.firstInteger(fallback: Int,vararg keys: String): Int=keys.firstOrNull { !isNull(it) }?.let(::getInt) ?: fallback
    private fun JSONObject.optionalText(key: String): String?=if(isNull(key)) null else getString(key)
    private fun JSONArray?.objects(): List<JSONObject> = if(this==null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if(this==null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONArray.integers(): List<Int> = (0 until length()).map { index -> val n=get(index);require(n is Number && n.toDouble()==n.toInt().toDouble()) { "周次必须为整数" };n.toInt() }
}
