package com.dolphin.schedule.domain

import java.time.LocalTime

/** 学校的午间占位不挪动原始节次；若确有课程占用，仍完整显示。 */
fun visiblePeriodIndices(schedule: Schedule): List<Int> = schedule.periods.indices.filter { index ->
    val label=schedule.periods[index].label.orEmpty().replace(" ","")
    val noon=label.startsWith("中午") || label.startsWith("午休")
    !noon || schedule.courses.any { index+1 in it.start..it.end }
}

fun displayTimetableRows(schedule: Schedule): List<TimetableRow> {
    val visible=visiblePeriodIndices(schedule)
    val normal=timetableRows(visible.map { schedule.periods[it] })
    val byIndex=visible.zip(normal).toMap()
    return schedule.periods.indices.map { byIndex[it] ?: TimetableRow(0f,0f) }
}

/** 只删除没有课程占用的节次；后续课程索引同步前移，实际时刻保持一致。 */
fun removePeriod(schedule: Schedule,index: Int): Schedule {
    require(index in schedule.periods.indices && schedule.periods.size>1) { "至少保留一个有效节次" }
    require(schedule.courses.none { index+1 in it.start..it.end }) { "这个节次有课程使用，请先调整相关课程，再删除时间" }
    return validateSchedule(schedule.copy(
        periods=schedule.periods.filterIndexed { i,_ -> i!=index },
        courses=schedule.courses.map { if(it.start>index+1)it.copy(start=it.start-1,end=it.end-1) else it },
    ))
}

/** 从真实时刻映射到绘制坐标；课间和收起的午休在相邻可见节次间连续插值。 */
fun timetableTimeOffset(periods: List<Period>,rows: List<TimetableRow>,time: LocalTime): Float? {
    val minute=time.toSecondOfDay()/60f
    val timed=periods.indices.mapNotNull { index ->
        if(rows.getOrNull(index)?.lesson==null || rows[index].lesson<=0f)return@mapNotNull null
        val start=runCatching { LocalTime.parse(periods[index].start).toSecondOfDay()/60f }.getOrNull() ?: return@mapNotNull null
        val end=runCatching { LocalTime.parse(periods[index].end).toSecondOfDay()/60f }.getOrNull() ?: return@mapNotNull null
        if(end<=start)return@mapNotNull null
        Triple(index,start,end)
    }
    var previous: Triple<Int,Float,Float>?=null
    fun top(index: Int)=rows.take(index).sumOf { it.height.toDouble() }.toFloat()
    timed.forEach { current ->
        val (index,start,end)=current
        if(minute in start..end)return top(index)+rows[index].lesson*(minute-start)/(end-start)
        val before=previous
        if(before!=null && minute>before.third && minute<start) {
            val y=top(before.first)+rows[before.first].lesson
            return y+(top(index)-y)*(minute-before.third)/(start-before.third)
        }
        previous=current
    }
    return null
}
