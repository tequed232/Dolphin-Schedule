package com.dolphin.schedule.ui.importing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.*

/** 教务、文件导入和撤销共用；明细只读，关闭不会确认或保存。 */
@Composable
internal fun SchedulePreviewSummary(before: Schedule,after: Schedule,showDate: Boolean=true) {
    val difference=remember(before,after) { compareSchedules(before,after) }
    var details by rememberSaveable(before,after) { mutableStateOf(false) }
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(after.term.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Text("${after.courses.size} 门课程 · ${after.term.weeks} 周 · ${after.periods.size} 节",style=MaterialTheme.typography.bodyMedium)
            if(showDate)Text("开学日期：${after.term.startDate}",style=MaterialTheme.typography.bodyMedium)
            Text(if(difference.hasChanges)"新增 ${difference.added.size} · 修改 ${difference.modified.size} · 移除 ${difference.removed.size}" else "课程、学期与作息没有变化",style=MaterialTheme.typography.bodyMedium)
            val changes=listOfNotNull(if(difference.termChanged)"学期名称" else null,if(difference.weeksChanged)"学期周数" else null,
                if(difference.dateChanged)"开学日期" else null,if(difference.timesChanged)"逐节作息" else null)
            if(changes.isNotEmpty())Text(changes.joinToString("、")+"有变化",style=MaterialTheme.typography.bodySmall)
            if(difference.hasChanges)TextButton(onClick={details=true}) { Text("查看变更明细") }
        }
    }
    if(details)ScheduleDifferenceSheet(before,after,difference) { details=false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDifferenceSheet(before: Schedule,after: Schedule,difference: ScheduleDifference,onClose: ()->Unit) {
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(onDismissRequest=onClose,sheetState=sheet) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f).testTag("schedule-difference-list"),
            contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("变更明细",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text("确认前不会改动当前课表。不能唯一对应的课程按新增和移除列出。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick=onClose) { Text("返回核对") }
            }
            if(difference.termChanged || difference.weeksChanged || difference.dateChanged)item {
                DifferenceCard("学期信息") {
                    if(difference.termChanged)FieldChange(DifferenceField("学期名称",before.term.name,after.term.name))
                    if(difference.weeksChanged)FieldChange(DifferenceField("学期周数","${before.term.weeks} 周","${after.term.weeks} 周"))
                    if(difference.dateChanged)FieldChange(DifferenceField("开学日期",before.term.startDate,after.term.startDate))
                }
            }
            if(difference.timesChanged)item { DifferenceHeading("作息变化 · ${difference.periodChanges.size} 项") }
            items(difference.periodChanges) { change ->
                DifferenceCard("时间轴位置 ${change.index+1}") {
                    FieldChange(DifferenceField("逐节作息",periodDescription(change.before,change.index),periodDescription(change.after,change.index)))
                }
            }
            if(difference.modified.isNotEmpty())item { DifferenceHeading("修改课程 · ${difference.modified.size} 项") }
            items(difference.modified) { change ->
                DifferenceCard(change.after.name) {
                    Text(courseWhen(change.after,after),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val fields=courseDifferenceFields(change,before,after)
                    fields.forEach { FieldChange(it) }
                    if(fields.isEmpty())Text("作息顺序已重排，上课时间未变。",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(difference.added.isNotEmpty())item { DifferenceHeading("新增课程 · ${difference.added.size} 项") }
            items(difference.added) { CourseDifferenceCard(it,after) }
            if(difference.removed.isNotEmpty())item { DifferenceHeading("移除课程 · ${difference.removed.size} 项") }
            items(difference.removed) { CourseDifferenceCard(it,before) }
            item { Text("未变化 ${difference.unchangedCount} 项",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DifferenceCard(title: String,content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DifferenceHeading(text: String) { Text(text,Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }

@Composable
private fun FieldChange(field: DifferenceField) {
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(field.label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text("原来：${field.before}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("现在：${field.after}",style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CourseDifferenceCard(course: Course,schedule: Schedule) {
    DifferenceCard(course.name) {
        Text(courseWhen(course,schedule),style=MaterialTheme.typography.bodyMedium)
        Text("教师：${course.teacher.ifBlank { "未填写" }}",style=MaterialTheme.typography.bodySmall)
        Text("地点：${course.room.ifBlank { "未填写" }}",style=MaterialTheme.typography.bodySmall)
        if(course.notes.isNotBlank())Text("备注：${course.notes}",style=MaterialTheme.typography.bodySmall)
    }
}

private fun courseWhen(course: Course,schedule: Schedule): String {
    val date=course.specificDate ?: "${weekdayLabel(course.day)} · 第 ${compactWeeks(course.weeks).ifBlank { "无" }} 周"
    val start=schedule.periods.getOrNull(course.start-1)?.start.orEmpty()
    val end=schedule.periods.getOrNull(course.end-1)?.end.orEmpty()
    return listOf(date,coursePeriod(course,schedule),if(start.isNotBlank() && end.isNotBlank())"$start—$end" else "时间待补充").joinToString(" · ")
}

private fun periodDescription(period: Period?,index: Int): String {
    if(period==null)return "无此节次"
    val time=if(period.start.isBlank() && period.end.isBlank())"时间未填写" else "${period.start}—${period.end}"
    val rest=period.breakAfter?.let { "；${it.label} ${it.start}—${it.end}" }.orEmpty()
    return "第 ${periodLabel(period,index)} 节 $time$rest"
}
