package com.dolphin.schedule.domain

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class AcademicBlock(val name: String,val weeks: String,val teacher: String,val room: String)
data class AcademicRow(val label: String,val start: String,val end: String,val cells: List<List<AcademicBlock>>)
data class AcademicSnapshot(val term: String,val startWeek: Int,val endWeek: Int,val rows: List<AcademicRow>,val termStartDate: String?=null)
/** 网页只负责读取原始字段；校验、周次、合并、稳定标识和撤销在 Kotlin 执行。 */
object AcademicImporter {
    fun convert(value: AcademicSnapshot,current: Schedule,confirmedDate: String?=null): Schedule {
        require(Regex("\\d{4}-\\d{4}-[12]").matches(value.term)) { "无法确定教务课表学期" }
        require(value.startWeek==1 && value.endWeek in 1..60) { "请查询从第 1 周开始的完整学期" }
        require(value.rows.size in 1..MAX_PERIODS) { "未读取到完整的逐节作息" }
        val startDate=confirmedDate ?: value.termStartDate ?: throw IllegalArgumentException("请先设置开学日期，再预览课程")
        require(runCatching { LocalDate.parse(startDate).year in 1900..9999 }.getOrDefault(false)) { "学期开始日期无效" }
        var ordinary=0;var noon=0;var noonEnded=false;var previous: LocalTime?=null;var blocks=0
        val courses=mutableListOf<Course>()
        value.rows.forEachIndexed { index,row ->
            require(row.cells.size==7) { "星期列缺失，原课表保留" }
            if(Regex("中午[1-9]\\d?").matches(row.label)) {
                require(!noonEnded && ++noon==row.label.removePrefix("中午").toInt()) { "中午节次顺序异常" }
            } else {
                if(noon>0) noonEnded=true
                require(Regex("[1-9]\\d?").matches(row.label) && ++ordinary==row.label.toInt()) { "节次不连续，请查询完整时间轴" }
            }
            require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(row.start) && Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(row.end)) { "逐节时间无效" }
            val start=LocalTime.parse(row.start);val end=LocalTime.parse(row.end)
            require(start<end && (previous==null || start>=previous)) { "逐节时间重叠或倒序" };previous=end
            row.cells.forEachIndexed { day,cell ->
                require(cell.size<=100) { "同一时段的课程过多" }
                cell.forEach { block ->
                    require(++blocks<=2000 && block.name.isNotBlank() && block.name.length<=300 && block.teacher.length<=500 && block.room.length<=500 && block.weeks.length<=1000) { "课程字段缺失或过长" }
                    val room=block.room.trim().takeUnless { it in listOf("--","—","无","待定") } ?: ""
                    courses+=Course(name=block.name.trim(),teacher=block.teacher.trim(),room=room,day=day+1,start=index+1,end=index+1,weeks=parseWeeks(block.weeks,value.endWeek))
                }
            }
        }
        require(ordinary>0 && courses.isNotEmpty()) { "本次没有有效课程，原课表保留" }
        val grouped=courses.groupBy { listOf(it.name,it.teacher,it.room,it.day.toString(),it.weeks.joinToString(",")) }
        val merged=mutableListOf<Course>()
        grouped.values.forEach { group ->
            var last: Course?=null
            group.distinctBy { it.start to it.end }.sortedBy { it.start }.forEach { course ->
                val prior=last
                val previousLabel=prior?.let { value.rows[it.end-1].label }
                val nextLabel=value.rows[course.start-1].label
                val sameKind=previousLabel!=null && previousLabel.startsWith("中午")==nextLabel.startsWith("中午")
                val adjacent=sameKind && nextLabel.removePrefix("中午").toInt()==previousLabel!!.removePrefix("中午").toInt()+1
                if(prior!=null && prior.end+1==course.start && adjacent) {
                    last=prior.copy(end=course.end);merged[merged.lastIndex]=last!!
                } else { last=course;merged+=course }
            }
        }
        val existing=current.courses.associateBy(::contentKey);val identifiers=mutableMapOf<String,String>()
        val canonical=merged.distinctBy(::contentKey).map { course ->
            val key=contentKey(course);val old=existing[key]
            val id=old?.id ?: "gdcmxy-"+MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
            require(identifiers[id]==null || identifiers[id]==key) { "课程标识冲突，请保留原课表并反馈" };identifiers[id]=key
            course.copy(id=id,color=old?.color ?: COURSE_COLORS[(course.day+course.start)%COURSE_COLORS.size],source="academic:gdcmxy")
        }
        val notes=mutableListOf(if(confirmedDate!=null) "开学日期：使用你确认的日期。" else "开学日期：来自教务系统的同一学期，请核对。")
        if(canonical.any { it.room.isEmpty() })notes+="部分课程没有地点，已保留课程，可稍后补充。"
        return validateSchedule(Schedule(Term(value.term,startDate,value.endWeek),value.rows.map { Period(it.start,it.end,it.label) },canonical,notes),allowEmpty=false)
    }
    fun difference(before: Schedule,after: Schedule): ScheduleDifference = compareSchedules(before,after)
    fun apply(data: AppData,schedule: Schedule): AppData=data.copy(schedule=validateSchedule(schedule,allowEmpty=false),onboarded=true,pendingAcademic=null,
        academicImport=AcademicHistory("gdcmxy",schedule.term.name,Instant.now().toString(),data.schedule,schedule))
    fun undo(data: AppData,expected: Schedule): AppData {
        require(data.schedule==expected) { "课表已变化，请重新查看撤销预览" }
        val history=requireNotNull(data.academicImport) { "没有可撤销的教务导入" }
        return data.copy(schedule=validateSchedule(history.before),academicImport=null)
    }
    private fun contentKey(course: Course): String=listOf(course.name,course.teacher,course.room,course.day.toString(),course.start.toString(),course.end.toString(),course.weeks.distinct().sorted().joinToString(","),course.specificDate ?: "").joinToString("|") { "${it.length}:$it" }
}
