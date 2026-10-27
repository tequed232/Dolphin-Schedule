package com.dolphin.schedule.importing

import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@Suppress("UNCHECKED_CAST") internal fun map(value: Any?): Map<String,Any?> = value as? Map<String,Any?> ?: emptyMap()
internal fun pick(p: Map<String,Any?>,vararg names: String): Any?=names.firstNotNullOfOrNull { p[it] }
internal fun integer(value: Any?,label: String): Int {
    val number=text(value).toDoubleOrNull();require(number!=null && number.isFinite() && number==number.toInt().toDouble()) { "$label 必须为整数" };return number.toInt()
}
internal fun jsonValue(value: Any?): Any?=when(value) {
    null,JSONObject.NULL->null
    is JSONObject->value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
    is JSONArray->(0 until value.length()).map { jsonValue(value.get(it)) }
    else->value
}
internal fun numbers(value: Any?,max: Int,label: String): List<Int> {
    if(value is List<*>) { require(value.isNotEmpty()) { "$label 没有可用数字" };return value.flatMap { numbers(it,max,label) }.distinct().sorted() }
    var source=text(value).replace(Regex("[第周节\\s]"),"").replace(Regex("[，、；;]"),",").replace(Regex("[~～至—–]"),"-")
    if(label=="周次" && source in listOf("单","双","每","全部","全","全年","全学年"))source="1-$max"+if(source in listOf("单","双"))source else ""
    require(source.isNotEmpty()) { "$label 不能为空" }
    val result=sortedSetOf<Int>()
    source.split(',').forEach { part ->
        require(!(part.contains('单') && part.contains('双'))) { "同一段不能同时指定单双周" }
        val parity=if(part.contains('单'))1 else if(part.contains('双'))0 else null
        val match=Regex("(\\d+)(?:-(\\d+))?").matchEntire(part.replace(Regex("[单双()（）]"),"")) ?: error("$label 格式错误：${text(value)}")
        val from=match.groupValues[1].toIntOrNull() ?: 0;val to=match.groupValues[2].toIntOrNull() ?: from
        require(from in 1..max && to in from..max) { "$label 应在 1～$max 范围内，且起止顺序正确" }
        (from..to).filter { parity==null || it%2==parity }.forEach(result::add)
    }
    require(result.isNotEmpty()) { "$label 没有可用数字" };return result.toList()
}
internal fun day(value: Any?): Int {
    val source=text(value).replace(Regex("^(星期|礼拜|周)"),"").lowercase(java.util.Locale.ROOT)
    val aliases=mapOf("一" to 1,"二" to 2,"三" to 3,"四" to 4,"五" to 5,"六" to 6,"日" to 7,"天" to 7,"mon" to 1,"monday" to 1,"tue" to 2,"tuesday" to 2,"wed" to 3,"wednesday" to 3,"thu" to 4,"thursday" to 4,"fri" to 5,"friday" to 5,"sat" to 6,"saturday" to 6,"sun" to 7,"sunday" to 7)
    val result=aliases[source] ?: source.toIntOrNull();require(result!=null && result in 1..7) { "无法识别星期：${text(value)}" };return result
}
internal fun flattened(value: Any?): Map<String,Any?> {
    val root=if(value is List<*>)mapOf("courses" to value) else map(value)
    val wrapped=pick(root,"schedule","data","result")
    return (root+(if(wrapped is List<*>)mapOf("courses" to wrapped) else map(wrapped))).filterKeys { it !in listOf("schedule","data","result") }
}
data class FilePreview(val schedule: Schedule,val errors: List<String> = emptyList(),val onlyConfiguration: Boolean=false)
object FileNormalizer {
    const val MAX_BYTES=5_000_000
    fun json(raw: String,current: Schedule): FilePreview {
        require(raw.length<=MAX_BYTES) { "文件超过 5 MB" }
        val source=raw.trim().replace(Regex("^```(?:json)?\\s*",RegexOption.IGNORE_CASE),"").replace(Regex("\\s*```$"),"")
        val start=source.indexOfFirst { it=='{' || it=='[' };require(start>=0) { "JSON 中没有对象或数组" }
        var quoted=false;var escaped=false;val stack=mutableListOf<Char>();var finish=-1
        for(i in start until source.length) {
            val c=source[i]
            if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false;continue}
            if(c=='"'){quoted=true;continue}
            if(c=='{' || c=='['){stack+=c;require(stack.size<=63) { "JSON 嵌套过深" }}
            else if(c=='}' || c==']'){require(stack.isNotEmpty() && stack.removeAt(stack.lastIndex)==if(c=='}')'{' else '[') { "JSON 括号不匹配" };if(stack.isEmpty()){finish=i+1;break}}
        }
        require(finish>start) { "JSON 括号未闭合" }
        val data=try { flattened(jsonValue(AppDataCodec.parseObject("{\"payload\":"+source.substring(start,finish)+"}").get("payload"))) } catch(failure: Exception) { throw IllegalArgumentException("JSON 语法错误，请检查括号、逗号和双引号",failure) }
        return try { normalize(data,current) } catch(original: Exception) {
            val courses=pick(data,"courses","lessons","items","list") as? List<*> ?: throw original
            if(courses.isEmpty() || courses.size>2000)throw original
            val valid=mutableListOf<Any?>();val errors=mutableListOf<String>()
            courses.forEachIndexed { i,c -> runCatching { normalize(data+mapOf("courses" to listOf(c)),current) }.onSuccess { valid+=c }.onFailure { errors+="第 ${i+1} 条：${it.message}" } }
            if(valid.isEmpty())throw original
            normalize(data+mapOf("courses" to valid,"lessons" to null,"items" to null,"list" to null),current).let { it.copy(errors=errors,schedule=it.schedule.copy(importNotes=it.schedule.importNotes+"跳过 ${errors.size} 项，请核对错误明细。")) }
        }
    }
    internal fun normalize(data: Map<String,Any?>,current: Schedule): FilePreview {
        val term=map(data["term"] ?: data["semester"]);val notes=mutableListOf<String>()
        val termName=text(term["name"] ?: data["termName"] ?: (data["term"] as? String)).ifBlank { current.term.name }
        val suppliedDate=text(pick(term,"startDate","start") ?: pick(data,"startDate","semesterStart","termStart"))
        val date=suppliedDate.ifBlank { current.term.startDate };require(runCatching { LocalDate.parse(date).year in 1900..9999 }.getOrDefault(false)) { "开学日期应为有效的 YYYY-MM-DD" }
        if(suppliedDate.isEmpty())notes+="文件未提供开学日期，保留当前配置。"
        val declared=term["weeks"] ?: data["totalWeeks"];var weeks=declared?.let { integer(it,"学期周数") } ?: current.term.weeks
        require(weeks in 1..999) { "学期周数应为 1～999" }
        var periods=current.periods
        val rawPeriods=data["periods"] as? List<*>
        val matrix=rawPeriods?.any { map(it)["days"] is List<*> }==true
        val inputTimes=pick(data,"times","periodTimes","lessonTimes","作息时间") ?: rawPeriods?.takeUnless { matrix }
        val rawCourses=pick(data,"courses","lessons","items","list")
        val backup=term["name"] is String && term["startDate"] is String && term["weeks"]!=null && rawPeriods?.isNotEmpty()==true && !matrix && rawCourses is List<*> && rawCourses.all { map(it)["id"] is String && text(map(it)["id"]).isNotBlank() }
        val rests=mutableListOf<RestTime>()
        fun addRest(raw: Any?,label: String="午休") { val time=timing(raw) ?: error("$label 缺少开始和结束时间");rests+=RestTime(label,time.first,time.second) }
        pick(data,"lunchBreak","午休")?.let { addRest(it) }
        pick(data,"breaks","rests")?.let { raw -> require(raw is List<*>) { "休息时间必须是数组" };raw.forEach { r -> val p=map(r);addRest(r,restLabel(pick(p,"label","name","type")).ifBlank { text(pick(p,"label","name")).ifBlank { "休息" } }) } }
        if(matrix)periods=rawPeriods!!.mapIndexed { i,raw ->
            val p=map(raw);val label=text(pick(p,"period","name") ?: "${i*2+1}-${i*2+2}").removePrefix("第").removeSuffix("节")
            val time=pick(p,"time","timeRange")?.let { timing(it) }
            if(time!=null)Period(time.first,time.second,label)
            else if(Regex("中午|午间").containsMatchIn(label))Period("","",label)
            else { val slots=numbers(label,24,"节次");val defaults=defaultSchedule().periods;Period(defaults.getOrNull(slots.first()-1)?.start ?: "",defaults.getOrNull(slots.last()-1)?.end ?: "",label) }
        }
        if(inputTimes!=null) {
            require(inputTimes is List<*> && inputTimes.isNotEmpty() && inputTimes.size<=48) { "节次时间必须是非空数组，课程节数为 1～24" }
            val lessons=mutableListOf<Any?>()
            inputTimes.forEach { raw -> val p=map(raw);val label=restLabel(pick(p,"label","name","period","section","节次","type"));if(label.isNotEmpty())addRest(raw,label) else lessons+=raw }
            require(lessons.size in 1..24) { "课程节数应为 1～24，休息不占课程节数" }
            val ordered=if(lessons.any { map(it)["index"]!=null })lessons.mapIndexed { i,r -> integer(map(r)["index"] ?: i+1,"times.index") to r }.sortedBy { it.first }.also { a -> require(a.map { it.first }==(1..a.size).toList()) { "times.index 应从 1 开始连续且不重复" } }.map { it.second } else lessons
            periods=ordered.mapIndexed { i,r -> val p=map(r);val time=timing(r);if(p["breakAfter"]!=null)addRest(p["breakAfter"],text(map(p["breakAfter"])["label"]).ifBlank { "休息" });require(time!=null || backup) { "第 ${i+1} 节没有完整时间" };Period(time?.first ?: "",time?.second ?: "",text(p["label"]).ifBlank { null }) }
        }
        val count=pick(data,"courseCount","sectionCount","periodCount")?.let { integer(it,"课程节数") }
        if(count!=null){require(count in 1..24) { "课程节数应为 1～24" };require(inputTimes==null || periods.size==count) { "courseCount 与 times 数量不一致" }}
        val first=pick(data,"firstStart","firstCourseStart","firstLessonStart")?.let(::normalizeClock)
        val duration=pick(data,"duration","courseDuration","lessonDuration")?.let { integer(it,"课时") }
        val gap=pick(data,"breakMinutes","breakDuration","breakTime")?.let { integer(it,"课间") }
        if(inputTimes==null && !matrix) {
            if(duration!=null && gap!=null)periods=retainRests(generatePeriods(count ?: periods.size,first ?: periods.first().start,duration,gap).mapIndexed { i,p -> periods.getOrNull(i)?.copy(start=p.start,end=p.end) ?: p })
            else { if(count!=null && count<periods.size)periods=resizePeriods(periods,count);if(first!=null || duration!=null || gap!=null)periods=retimePeriods(periods,first ?: periods.first().start,duration,gap);if(count!=null)periods=resizePeriods(periods,count);if(duration!=null || gap!=null)periods=retimePeriods(periods,periods.first().start,duration,gap) }
        }
        periods=applyRests(checkedPeriods(periods),rests)
        val hasList=listOf("courses","lessons","items","list").any { data[it]!=null }
        val config=term.isNotEmpty() || data["term"] is String || inputTimes!=null || rests.isNotEmpty() || listOf(count,first,duration,gap,pick(data,"termName","startDate","semesterStart","termStart","totalWeeks")).any { it!=null }
        if(!hasList && !matrix && config) {
            notes+="文件仅更新课表配置，已有课程和 ID 保留。"
            return FilePreview(validateSchedule(Schedule(Term(termName,date,weeks),periods,current.courses,notes)),onlyConfiguration=true)
        }
        val input: List<Any?> = if(matrix) {
            rawPeriods!!.flatMapIndexed { i,r ->
                val days=map(r)["days"] as? List<*> ?: error("第 ${i+1} 个 period 缺少 days 数组")
                require(days.size<=7) { "星期列超过 7 列" }
                days.flatMapIndexed { d,cells ->
                    (if(cells is List<*>)cells else listOf(cells)).filter { it!=null && text(it)!="" }.map { cell ->
                        (if(cell is String)mapOf("name" to cell) else map(cell))+mapOf("day" to d+1,"start" to i+1,"end" to i+1)
                    }
                }
            }
        } else rawCourses as? List<*> ?: error("找不到 courses 或包含 days 的 periods 数组")
        require(input.isNotEmpty() || backup) { "没有可导入的课程，原课表保留" };require(input.size<=2000) { "课程超过 2000 条，请按学年拆分" }
        val explicit=input.map { raw -> val value=pick(map(raw),"weeks","week","weekRange","weeksText","周次");if(backup && value is List<*> && value.isEmpty())emptyList() else if(value==null || text(value).isBlank() || Regex("(?:单周|双周|每周|全周|全部|全年|全学年)").matches(text(value)))null else numbers(value,999,"周次") }
        val detected=explicit.mapNotNull { it?.maxOrNull() }.maxOrNull() ?: 0
        if(!backup)weeks=maxOf(weeks,detected)
        require(explicit.sumOf { it?.size ?: weeks }<=20_000) { "课次超过 20000 次，请按学年拆分后导入" }
        if(declared==null)notes+=if(detected>current.term.weeks)"已从课程推断为 $weeks 周。" else "文件未标明学期周数，保留当前配置。"
        val courses=input.mapIndexed { i,raw ->
            try {
                val c=map(raw);val name=text(pick(c,"name","course","courseName","title","课程名","课程名称"));require(name.isNotBlank()) { "缺少课程名" }
                val sections=if(!matrix)pick(c,"sections","periods","period","section","periodName","节次","时段") else null
                val from=pick(c,"start","startPeriod","startSection","section","periodStart","节次起");val to=pick(c,"end","endPeriod","endSection","section","periodEnd","节次止") ?: from
                val slots=numbers(sections ?: "${text(from)}-${text(to)}",if(backup)24 else periods.size,"节次")
                require(slots.size==slots.last()-slots.first()+1) { "节次必须连续，请拆成多条课程" }
                val week=pick(c,"weeks","week","weekRange","weeksText","周次");if(week==null || text(week).isBlank())notes+="第 ${i+1} 条未填写周次，按本学期每周上课处理。"
                val originalRoom=text(pick(c,"room","classroom","location","place","教室"));val room=if(backup)originalRoom else normalizedRoom(originalRoom)
                val specific=text(c["specificDate"]).ifBlank { null };specific?.let { require(runCatching { LocalDate.parse(it).year in 1900..9999 }.getOrDefault(false)) { "课程日期无效" } }
                Course(id=if(backup)c["id"] as String else java.util.UUID.randomUUID().toString(),name=name,teacher=text(pick(c,"teacher","teacherName","教师","老师")),room=room,
                    day=specific?.let { LocalDate.parse(it).dayOfWeek.value } ?: day(pick(c,"day","weekday","weekDay","dayOfWeek","星期","周几")),start=slots.first(),end=slots.last(),
                    weeks=explicit[i] ?: numbers(if(week==null || text(week).isBlank())"1-$weeks" else week,weeks,"周次"),color=text(c["color"]).takeIf { it in COURSE_COLORS } ?: COURSE_COLORS[i%COURSE_COLORS.size],
                    notes=listOf(text(pick(c,"notes","note","备注")),if(!backup && room!=originalRoom && Regex("[/／]").containsMatchIn(originalRoom))"原始教室：$originalRoom" else "").filter { it.isNotEmpty() }.joinToString("\n"),temporary=c["temporary"]==true,specificDate=specific)
            } catch(failure: Exception) { throw IllegalArgumentException("第 ${i+1} 条课程：${failure.message}",failure) }
        }
        val courseTimes=input.mapIndexedNotNull { i,r -> val p=map(r);val time=timing(mapOf("startTime" to pick(p,"startTime","timeStart","上课时间","开始时间"),"endTime" to pick(p,"endTime","timeEnd","下课时间","结束时间"),"timeRange" to pick(p,"timeRange","time","时间段","时间"))) ?: return@mapIndexedNotNull null;ImportedTime(courses[i].start,courses[i].end,time.first,time.second,i+1) }
        if(inputTimes!=null)courseTimes.forEach { require(periods[it.first-1].start==it.start && periods[it.last-1].end==it.end) { "第 ${it.row} 条课程的时间与逐节时间表不一致" } }
        if(courseTimes.isNotEmpty())periods=applyRests(applyTimes(current.copy(periods=periods),courses,courseTimes,notes),rests)
        return FilePreview(validateSchedule(Schedule(Term(termName,date,weeks),periods,courses,notes.distinct())))
    }
    private fun normalizedRoom(value: String): String {
        val clean=value.replace(Regex("^(?:教室|地点)[:：]\\s*"),"").replace(Regex("\\s+")," ").trim()
        if(Regex("(?:\\.{2,}|…|待定)").containsMatchIn(clean))return clean
        val schoolless=clean.replace(Regex("^.{2,25}?(?:大学|学院|学校)(?:.{0,12}?校区)?(?=.{1,20}(?:楼|栋|教室))"),"").trim()
        val numbered=Regex("(\\d{1,3})\\s*[-－—栋楼#]\\s*([A-Za-z]?\\d{2,4})(?:\\s*(?:号)?(?:教室|室))?(?:\\s*[/／]\\s*([A-Za-z]?\\d{2,4}))*").matchEntire(schoolless)
        if(numbered!=null)return "${numbered.groupValues[1].toInt()}栋${numbered.groupValues[2].uppercase(java.util.Locale.ROOT)}号教室"
        val named=Regex("(.{1,20}?(?:楼|栋))\\s*([A-Za-z]?\\d{2,4})(?:\\s*(?:号)?(?:教室|室))?").matchEntire(schoolless)
        return if(named!=null)named.groupValues[1]+named.groupValues[2].uppercase(java.util.Locale.ROOT)+"号教室" else schoolless
    }
}
