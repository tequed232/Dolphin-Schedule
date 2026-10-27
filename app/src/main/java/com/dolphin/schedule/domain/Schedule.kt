package com.dolphin.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class Term(val name: String, val startDate: String, val weeks: Int)
data class RestTime(val label: String, val start: String, val end: String)
data class Period(val start: String, val end: String, val label: String? = null, val breakAfter: RestTime? = null)
data class Course(
    val id: String = UUID.randomUUID().toString(), val name: String, val teacher: String = "", val room: String = "",
    val day: Int, val start: Int, val end: Int, val weeks: List<Int>, val color: String = "sage", val notes: String = "",
    val temporary: Boolean = false, val specificDate: String? = null,
)
data class Schedule(val term: Term, val periods: List<Period>, val courses: List<Course> = emptyList(), val importNotes: List<String> = emptyList())
data class Book(val title: String = "", val publisher: String = "", val edition: String = "", val cover: String? = null, val text: String = "", val source: String = "manual")
data class Background(val url: String, val name: String, val width: Int, val height: Int, val sourceWidth: Int, val sourceHeight: Int)
data class AcademicHistory(val school: String, val term: String, val importedAt: String, val before: Schedule, val after: Schedule)
data class AppData(
    val schedule: Schedule = defaultSchedule(), val books: Map<String, Book> = emptyMap(), val settings: Preferences = Preferences(),
    val background: Background? = null, val onboarded: Boolean = false, val academicImport: AcademicHistory? = null,
)
data class Preferences(
    val timetableMode: String = "list",
    val showTimes: Boolean = true, val showCurrentTimeLine: Boolean = true, val mode: String = "system", val scale: Float = 1f,
    val showNavigation: Boolean = true, val showWeekend: Boolean = true,
    val topAuto: Boolean = true, val bottomAuto: Boolean = true, val topInset: Int = 0, val bottomInset: Int = 0,
    val backgroundEnabled: Boolean = false, val backgroundBlur: Int = 0, val performance: String = "auto",
    val notificationsEnabled: Boolean = true, val journeyLive: Boolean = true,
    val school: String = "", val map: String = "amap", val dynamicColor: Boolean = true,
)

const val MAX_PERIODS = 24
val COURSE_COLORS = listOf("sage", "lavender", "peach", "blue", "rose")

fun defaultSchedule(): Schedule {
    val times = listOf("08:00-08:45","08:55-09:40","10:00-10:45","10:55-11:40","14:00-14:45","14:55-15:40","16:00-16:45","16:55-17:40","19:00-19:45","19:55-20:40","20:50-21:35","21:45-22:30")
    return Schedule(Term("我的课表", LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),20),times.map { val parts=it.split('-'); Period(parts[0],parts[1]) })
}

fun firstMonday(start: String): LocalDate = LocalDate.parse(start).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun weekOf(date: LocalDate, start: String): Int = Math.floorDiv(ChronoUnit.DAYS.between(firstMonday(start),date),7).toInt()+1
fun coursesOn(schedule: Schedule, date: LocalDate): List<Course> {
    val week=weekOf(date,schedule.term.startDate)
    return schedule.courses.filter { course -> course.start >= 1 && course.end <= schedule.periods.size &&
        (course.specificDate?.let { it == date.toString() } ?: (course.day == date.dayOfWeek.value && week in course.weeks))
    }.sortedWith(compareBy<Course> { it.start }.thenBy { it.end }.thenBy { it.id })
}
fun courseDates(course: Course, schedule: Schedule): List<LocalDate> = course.specificDate?.let { listOf(LocalDate.parse(it)) }
    ?: course.weeks.distinct().sorted().map { firstMonday(schedule.term.startDate).plusDays((it-1)*7L+course.day-1) }
fun periodLabel(period: Period, index: Int): String = period.label?.takeIf { it.isNotBlank() } ?: (index+1).toString()
fun coursePeriod(course: Course, schedule: Schedule): String {
    val first=schedule.periods.getOrNull(course.start-1)?.let { periodLabel(it,course.start-1) } ?: course.start.toString()
    val last=schedule.periods.getOrNull(course.end-1)?.let { periodLabel(it,course.end-1) } ?: course.end.toString()
    return if(course.start==course.end) "第 $first 节" else "第 $first 至 $last 节"
}
fun validateSchedule(schedule: Schedule, allowEmpty: Boolean = true): Schedule {
    require(schedule.term.name.length in 1..300) { "请填写课表名称" }
    require(runCatching { LocalDate.parse(schedule.term.startDate).year in 1900..9999 }.getOrDefault(false)) { "开学日期无效" }
    require(schedule.term.weeks in 1..999) { "学期周数应为 1～999 周" }
    require(schedule.periods.size in 1..MAX_PERIODS) { "课程节数应为 1～$MAX_PERIODS 节" }
    var previous: LocalTime?=null
    schedule.periods.forEachIndexed { index,p ->
        require(p.label == null || p.label.length <= 30) { "第 ${index+1} 节名称过长" }
        if(p.start.isBlank() && p.end.isBlank()) return@forEachIndexed
        require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(p.start) && Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(p.end)) { "第 ${index+1} 节时间应为 HH:mm" }
        val start=runCatching { LocalTime.parse(p.start) }.getOrNull(); val end=runCatching { LocalTime.parse(p.end) }.getOrNull()
        require(start!=null && end!=null && start<end && (previous==null || start>=previous)) { "第 ${index+1} 节时间缺失、重叠或倒序" }
        previous=end
        p.breakAfter?.let { rest ->
            val restStart=runCatching { LocalTime.parse(rest.start) }.getOrNull();val restEnd=runCatching { LocalTime.parse(rest.end) }.getOrNull()
            val nextStart=schedule.periods.getOrNull(index+1)?.start?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) }
            require(rest.label.isNotBlank() && rest.label.length<=80 && restStart!=null && restEnd!=null && restStart<restEnd && restStart>=end && (nextStart==null || restEnd<=nextStart)) { "第 ${index+1} 节课后的休息时间无效或与课程重叠" }
        }
    }
    require(schedule.courses.size <= 10000 && (allowEmpty || schedule.courses.isNotEmpty())) { "课表为空或课程过多" }
    require(schedule.courses.map { it.id }.distinct().size == schedule.courses.size) { "课程标识重复" }
    schedule.courses.forEach { c ->
        require(c.id.isNotBlank() && c.id.length<=200 && c.name.isNotBlank() && c.name.length<=300) { "课程名称或标识无效" }
        require(c.day in 1..7 && c.start in 1..MAX_PERIODS && c.end in c.start..MAX_PERIODS) { "${c.name}的星期或节次无效" }
        // 历史备份允许保留已隐藏节次和显式空周次，不擅自扩大课程范围。
        require(c.weeks.all { it in 1..999 }) { "${c.name}的周次无效" }
        require(c.teacher.length<=500 && c.room.length<=500 && c.notes.length<=10000) { "${c.name}的文字过长" }
        require(c.specificDate==null || runCatching { LocalDate.parse(c.specificDate); true }.getOrDefault(false)) { "${c.name}的指定日期无效" }
    }
    return schedule
}

fun parseWeeks(value: String, maximum: Int): List<Int> {
    require(maximum in 1..999)
    val text=value.trim().replace(Regex("[第周\\s]"),"").replace(Regex("[，、；;]"),",").replace(Regex("[~～至—–]"),"-")
    if(text in listOf("每","全","全部","整学期")) return (1..maximum).toList()
    if(text=="单" || text=="双") return (1..maximum).filter { it%2==if(text=="单") 1 else 0 }
    val result=sortedSetOf<Int>()
    require(text.isNotEmpty()) { "请填写周次" }
    text.split(',').forEach { item ->
        val match=Regex("(\\d+)(?:-(\\d+))?([单双])?").matchEntire(item) ?: throw IllegalArgumentException("无法识别周次：$item")
        val start=match.groupValues[1].toIntOrNull() ?: 0; val end=match.groupValues[2].toIntOrNull() ?: start
        require(start in 1..maximum && end in start..maximum) { "周次应在 1～$maximum 范围内" }
        (start..end).filter { match.groupValues[3].isEmpty() || it%2==if(match.groupValues[3]=="单") 1 else 0 }.forEach(result::add)
    }
    require(result.isNotEmpty()) { "没有有效的上课周次" }
    return result.toList()
}
fun compactWeeks(weeks: List<Int>): String {
    val sorted=weeks.distinct().sorted(); val parts=mutableListOf<String>(); var index=0
    while(index<sorted.size) { val start=sorted[index];var end=start;while(index+1<sorted.size && sorted[index+1]==end+1){index++;end=sorted[index]};parts+=if(start==end) "$start" else "$start-$end";index++ }
    return parts.joinToString(";")
}
fun coursesConflict(a: Course,b: Course,schedule: Schedule): Boolean {
    if(a.id==b.id || a.start>b.end || b.start>a.end) return false
    if(a.specificDate!=null || b.specificDate!=null) return courseDates(a,schedule).toSet().intersect(courseDates(b,schedule).toSet()).isNotEmpty()
    return a.day==b.day && a.weeks.any { it in b.weeks }
}
