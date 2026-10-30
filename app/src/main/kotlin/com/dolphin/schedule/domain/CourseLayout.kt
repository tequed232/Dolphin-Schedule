package com.dolphin.schedule.domain

import java.time.LocalDate
import kotlin.math.abs

enum class LayoutEdge { MOVE,START,END }
class LayoutConflict(val overlaps: List<Course>): IllegalArgumentException("调整后与其他课程时间重叠")

/** 用真实行高吸附；收起的午休不是拖放目标，跨节课程保持原有连续节数。 */
fun layoutCandidate(schedule: Schedule,course: Course,date: LocalDate,edge: LayoutEdge,dayDelta: Int,verticalDelta: Float,rows: List<TimetableRow>): Course {
    require(verticalDelta.isFinite() && rows.size==schedule.periods.size)
    require(course.start in 1..rows.size && course.end in course.start..rows.size)
    val visible=rows.indices.filter { rows[it].lesson>0f }.toSet()
    val tops=rows.runningFold(0f) { y,row -> y+row.height }
    val ends=rows.indices.map { tops[it]+rows[it].lesson }
    fun contiguous(start: Int,end: Int)=(start..end).all { it in visible }
    val length=course.end-course.start
    val candidates=visible.filter { index -> when(edge) {
        LayoutEdge.MOVE -> index+length<rows.size && contiguous(index,index+length)
        LayoutEdge.START -> index<=course.end-1 && contiguous(index,course.end-1)
        LayoutEdge.END -> index>=course.start-1 && contiguous(course.start-1,index)
    } }
    if(candidates.isEmpty())return course
    val origin=if(edge==LayoutEdge.END)ends[course.end-1] else tops[course.start-1]
    val target=origin+verticalDelta
    val index=candidates.minBy { abs((if(edge==LayoutEdge.END)ends[it] else tops[it])-target) }
    val start=if(edge==LayoutEdge.END)course.start else index+1
    val end=when(edge) { LayoutEdge.START->course.end;LayoutEdge.END->index+1;LayoutEdge.MOVE->start+length }
    val originalDay=if(course.specificDate!=null)date.dayOfWeek.value else course.day
    val day=if(edge==LayoutEdge.MOVE)(originalDay.toLong()+dayDelta).coerceIn(1,7).toInt() else originalDay
    return course.copy(start=start,end=end,day=day,specificDate=course.specificDate?.let { date.plusDays((day-originalDay).toLong()).toString() })
}

/** 保存时重读课程和冲突；只允许改变位置，不覆盖名称、周次、教材或其他窗口的修改。 */
fun applyLayoutChange(current: Schedule,baseline: Schedule,original: Course,next: Course,approvedConflicts: List<Course>?=null): Schedule {
    require(current.term==baseline.term && current.periods==baseline.periods && current.courses.find { it.id==original.id }==original) { "课程或作息已变化，请重新长按调整；原数据保留" }
    require(next.copy(day=original.day,start=original.start,end=original.end,specificDate=original.specificDate)==original) { "位置调整不能改写其他课程信息" }
    require(next.start in 1..current.periods.size && next.end in next.start..current.periods.size)
    val overlaps=current.courses.filter { coursesConflict(next,it,current) }
    if(overlaps.isNotEmpty() && (approvedConflicts==null || overlaps.toSet()!=approvedConflicts.toSet()))throw LayoutConflict(overlaps)
    return validateSchedule(current.copy(courses=current.courses.map { if(it.id==original.id)next.copy(source="manual") else it }))
}
