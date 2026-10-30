package com.dolphin.schedule.domain

data class AcademicMergePreview(val schedule: Schedule,val keptCount: Int,val conflicts: List<Pair<Course,Course>>)

/** 来源只由明确的写入操作或旧导入记录确定；不通过课名猜课程归属。 */
fun academicMerge(current: Schedule,school: Schedule,keepManual: Boolean): AcademicMergePreview {
    if(!keepManual)return AcademicMergePreview(school,0,emptyList())
    val preserved=current.courses.filter { it.source!="academic:gdcmxy" }
    if(preserved.isEmpty())return AcademicMergePreview(school,0,emptyList())
    require(current.term==school.term) { "学期名称、日期或周数不同，暂不能保留手动课程；请先分别核对，或关闭保留后导入" }
    fun remap(course: Course): Course {
        val indices=(course.start..course.end).map { index ->
            val period=current.periods.getOrNull(index-1) ?: error("${course.name}的原节次缺失，无法保留")
            require(period.start.isNotBlank() && period.end.isNotBlank()) { "${course.name}的原作息不完整，无法安全保留" }
            val matches=school.periods.indices.filter { school.periods[it].start==period.start && school.periods[it].end==period.end }
            require(matches.size==1) { "${course.name}的上课时间在新作息中不能唯一对应，请先调整或关闭保留" }
            matches.single()+1
        }
        require(indices.zipWithNext().all { (a,b)->b==a+1 }) { "${course.name}的连堂节次不再连续，无法安全保留" }
        return course.copy(start=indices.first(),end=indices.last())
    }
    fun identity(course: Course)=course.copy(id="",notes="",color="",source="",weeks=course.weeks.distinct().sorted())
    val manual=preserved.map(::remap)
    val result=school.courses.toMutableList()
    manual.forEach { course ->
        // 完全相同的课程合成一条，保留用户的备注、颜色与 ID；其他情况均不静默覆盖。
        val exact=result.indexOfFirst { it.source=="academic:gdcmxy" && identity(it)==identity(course) }
        val collision=result.indexOfFirst { it.id==course.id }.takeIf { it!=exact } ?: -1
        if(collision>=0) {
                val original=result[collision];var id=original.id+"-school"
                while(result.any { it.id==id } || manual.any { it.id==id })id+="x"
                require(id.length<=200) { "课程标识冲突，请先调整后重试" }
                result[collision]=original.copy(id=id)
        }
        if(exact>=0)result[exact]=course else result+=course
    }
    val merged=validateSchedule(school.copy(courses=result))
    val conflicts=mutableListOf<Pair<Course,Course>>()
    val seen=mutableSetOf<Set<String>>()
    // 只检查被保留课程涉及的冲突，UI 有界展示；不能因同一课程 ID 把冲突隐藏掉。
    for(course in manual)for(other in result) {
        if(course.id!=other.id && coursesConflict(course,other,merged) && seen.add(setOf(course.id,other.id))) {
            conflicts+=course to other
            require(conflicts.size<=1000) { "保留课程的冲突超过 1000 项，请先整理课程后再导入" }
        }
    }
    return AcademicMergePreview(merged,manual.size,conflicts)
}
