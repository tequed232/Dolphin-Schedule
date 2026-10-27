package com.dolphin.schedule.domain

import java.time.LocalTime

data class TimetableRow(val lesson: Float,val gap: Float) { val height: Float get()=lesson+gap }
data class PositionedCourse(val course: Course,val lane: Int,val lanes: Int)

fun timetableRows(periods: List<Period>): List<TimetableRow> = periods.mapIndexed { index,p ->
    val start=runCatching { LocalTime.parse(p.start).toSecondOfDay()/60 }.getOrDefault(480+index*55)
    val end=runCatching { LocalTime.parse(p.end).toSecondOfDay()/60 }.getOrDefault(start+45)
    val next=periods.getOrNull(index+1)?.start?.let { runCatching { LocalTime.parse(it).toSecondOfDay()/60 }.getOrNull() }
    val gap=next?.let { (it-end).coerceAtLeast(0) } ?: 0
    TimetableRow(((end-start)*1.45f).coerceIn(84f,120f),if(gap>0) (gap*.4f).coerceIn(4f,44f) else 0f)
}

/** 将相互重叠的课程按连通组分列；不同课程名称或地点不参与去重。 */
fun positionCourses(courses: List<Course>): List<PositionedCourse> {
    val ordered=courses.sortedWith(compareBy<Course> { it.start }.thenBy { it.end }.thenBy { it.id })
    val result=mutableListOf<PositionedCourse>();var group=mutableListOf<Course>();var end=0
    fun flush() {
        if(group.isEmpty())return
        val lanes=mutableListOf<Int>();val placed=mutableListOf<Pair<Course,Int>>()
        group.forEach { course ->
            val free=lanes.indexOfFirst { it<course.start };val lane=if(free<0) lanes.size else free
            if(free<0)lanes+=course.end else lanes[lane]=course.end
            placed+=course to lane
        }
        result+=placed.map { PositionedCourse(it.first,it.second,lanes.size) };group=mutableListOf();end=0
    }
    ordered.forEach { course -> if(group.isNotEmpty() && course.start>end)flush();group+=course;end=maxOf(end,course.end) }
    flush();return result
}

fun inferredRest(periods: List<Period>,index: Int): RestTime? {
    periods.getOrNull(index)?.breakAfter?.let { return it }
    val current=periods.getOrNull(index) ?: return null;val next=periods.getOrNull(index+1) ?: return null
    val end=runCatching { LocalTime.parse(current.end) }.getOrNull() ?: return null
    val start=runCatching { LocalTime.parse(next.start) }.getOrNull() ?: return null
    if(start<=end)return null
    return RestTime(if(java.time.Duration.between(end,start).toMinutes()>=45) "午休" else "课间",current.end,next.start)
}
