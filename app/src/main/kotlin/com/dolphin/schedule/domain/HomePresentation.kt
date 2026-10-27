package com.dolphin.schedule.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 连续日期按索引惰性展示，不创建整学年的日期数组。隐藏周末时仍允许选中周末。 */
class DateStripRange(val showWeekend: Boolean) {
    private val first=LocalDate.of(1900,1,1) // 周一
    private val last=LocalDate.of(9999,12,31)
    private val days=ChronoUnit.DAYS.between(first,last).toInt()+1
    val size=if(showWeekend)days else days/7*5+minOf(days%7,5)
    fun dateAt(index: Int): LocalDate {
        require(index in 0 until size)
        return first.plusDays(if(showWeekend)index.toLong() else index/5*7L+index%5)
    }
    fun indexOf(date: LocalDate): Int {
        require(date in first..last && (showWeekend || date.dayOfWeek.value<=5))
        val offset=ChronoUnit.DAYS.between(first,date).toInt()
        return if(showWeekend)offset else offset/7*5+offset%7
    }
}

data class HomeCourse(val course: Course,val inProgress: Boolean,val title: String)

/** 与原版一致：当天取尚未结束的一门，未来日期取第一门；过去日期不提示下一节。 */
fun homeCourse(schedule: Schedule,date: LocalDate,now: LocalDateTime): HomeCourse? {
    if(date<now.toLocalDate())return null
    val course=coursesOn(schedule,date).firstOrNull { course ->
        val end=runCatching { LocalTime.parse(schedule.periods[course.end-1].end) }.getOrNull()
        date>now.toLocalDate() || (end!=null && end>now.toLocalTime())
    } ?: return null
    val start=runCatching { LocalTime.parse(schedule.periods[course.start-1].start) }.getOrNull()
    val active=date==now.toLocalDate() && start!=null && now.toLocalTime()>=start
    return HomeCourse(course,active,if(active)"正在上课" else if(date==now.toLocalDate())"下一节课" else "当天第一节")
}
