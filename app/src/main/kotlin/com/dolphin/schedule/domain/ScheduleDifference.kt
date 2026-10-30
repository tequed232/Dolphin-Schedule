package com.dolphin.schedule.domain

data class CourseModification(val before: Course,val after: Course)
data class PeriodModification(val index: Int,val before: Period?,val after: Period?)
data class ScheduleDifference(
    val added: List<Course>,val removed: List<Course>,val modified: List<CourseModification>,
    val periodChanges: List<PeriodModification>,val dateChanged: Boolean,
    val termChanged: Boolean,val weeksChanged: Boolean,val unchangedCount: Int,
) {
    val timesChanged: Boolean get()=periodChanges.isNotEmpty()
    val hasChanges: Boolean get()=added.isNotEmpty() || removed.isNotEmpty() || modified.isNotEmpty() ||
        timesChanged || dateChanged || termChanged || weeksChanged
}

/** 仅生成预览，不修改课表。多条同名记录不能唯一对应时，保留为新增/移除。 */
fun compareSchedules(before: Schedule,after: Schedule): ScheduleDifference {
    fun content(course: Course)=course.copy(id="",weeks=course.weeks.distinct().sorted(),source="")
    val old=before.courses.indices.toMutableSet();val next=after.courses.indices.toMutableSet()
    val identical=after.courses.indices.groupBy { content(after.courses[it]) }
        .mapValues { (_,indices)->java.util.ArrayDeque(indices) }
    var unchanged=0
    before.courses.forEachIndexed { index,course ->
        val match=identical[content(course)]?.pollFirst()
        if(match!=null) { old.remove(index);next.remove(match);unchanged++ }
    }
    val changed=mutableListOf<CourseModification>()
    fun pairUnique(key: (Course)->Any?) {
        val oldGroups=old.groupBy { key(before.courses[it]) }
        val nextGroups=next.groupBy { key(after.courses[it]) }
        oldGroups.forEach { (identity,indices)->
            val matches=nextGroups[identity]
            if(identity!=null && indices.size==1 && matches?.size==1) {
                val a=indices.single();val b=matches.single()
                changed+=CourseModification(before.courses[a],after.courses[b]);old.remove(a);next.remove(b)
            }
        }
    }
    // 稳定 ID 为强关联；教务生成的内容 ID 变化后只使用唯一的课程位置或名称。
    pairUnique { it.id.takeIf(String::isNotBlank) }
    if(before.term.name==after.term.name) {
        pairUnique { listOf(it.name,it.day,it.start,it.end,it.specificDate) }
        pairUnique { it.name }
    }
    val periods=(0 until maxOf(before.periods.size,after.periods.size)).mapNotNull { index ->
        val a=before.periods.getOrNull(index);val b=after.periods.getOrNull(index)
        if(a==b)null else PeriodModification(index,a,b)
    }
    return ScheduleDifference(next.map { after.courses[it] },old.map { before.courses[it] },changed,periods,
        before.term.startDate!=after.term.startDate,before.term.name!=after.term.name,
        before.term.weeks!=after.term.weeks,unchanged)
}

data class DifferenceField(val label: String,val before: String,val after: String)

/** 只显示变化字段；有课表上下文时使用学校节次名称及实际时刻，不把数组位置当节次名。 */
fun courseDifferenceFields(change: CourseModification,before: Schedule?=null,after: Schedule?=null): List<DifferenceField> {
    val a=change.before;val b=change.after
    return buildList {
        fun field(label: String,old: String,next: String) { if(old!=next)add(DifferenceField(label,old.ifBlank { "未填写" },next.ifBlank { "未填写" })) }
        field("课程名",a.name,b.name);field("教师",a.teacher,b.teacher);field("地点",a.room,b.room)
        field("星期",weekdayLabel(a.day),weekdayLabel(b.day))
        field("节次与时间",sectionDescription(a,before),sectionDescription(b,after))
        field("周次",compactWeeks(a.weeks).ifBlank { "无上课周次" },compactWeeks(b.weeks).ifBlank { "无上课周次" })
        field("指定日期",a.specificDate ?: "每周课程",b.specificDate ?: "每周课程")
        field("临时课程",if(a.temporary)"是" else "否",if(b.temporary)"是" else "否")
        field("备注",a.notes,b.notes);field("颜色",courseColorLabel(a.color),courseColorLabel(b.color))
    }
}

fun weekdayLabel(day: Int): String=listOf("周一","周二","周三","周四","周五","周六","周日").getOrNull(day-1) ?: "星期 $day"
private fun sectionRange(course: Course)=if(course.start==course.end)"第 ${course.start} 节" else "第 ${course.start}—${course.end} 节"
private fun sectionDescription(course: Course,schedule: Schedule?): String {
    if(schedule==null)return sectionRange(course)
    val start=schedule.periods.getOrNull(course.start-1)?.start.orEmpty()
    val end=schedule.periods.getOrNull(course.end-1)?.end.orEmpty()
    return coursePeriod(course,schedule)+if(start.isNotBlank() && end.isNotBlank())" · $start—$end" else " · 时间待补充"
}
private fun courseColorLabel(color: String)=mapOf("sage" to "青绿","lavender" to "淡紫","peach" to "浅橙","blue" to "浅蓝","rose" to "粉红")[color] ?: color
