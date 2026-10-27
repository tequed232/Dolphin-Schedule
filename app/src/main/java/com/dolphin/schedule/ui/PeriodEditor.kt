package com.dolphin.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.*

data class PeriodEditPlan(val baseline: Schedule,val index: Int)

@Composable
internal fun PeriodEditor(plan: PeriodEditPlan,model: DolphinViewModel) {
    val original=plan.baseline.periods[plan.index]
    var label by rememberSaveable { mutableStateOf(original.label ?: (plan.index+1).toString()) }
    var start by rememberSaveable { mutableStateOf(original.start) }
    var end by rememberSaveable { mutableStateOf(original.end) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    fun save(updated: Schedule) { model.change("时间已更新",expected=plan.baseline,after={model.periodEdit=null}) { it.copy(schedule=updated) } }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("删除这个节次？")},text={Text("删除 ${periodLabel(original,plan.index)} · ${original.start}—${original.end}。后续课程会同步调整节次，实际上课时间保持一致。有课程占用的节次需要先调整课程。")},confirmButton={TextButton(enabled=!model.busy,onClick={runCatching { save(removePeriod(plan.baseline,plan.index));deleting=false }.onFailure { error=it.message;deleting=false }}) { Text("确认删除") }},dismissButton={TextButton(onClick={deleting=false}) { Text("取消") }})
    else AlertDialog(onDismissRequest={if(!model.busy)model.periodEdit=null},title={Text("编辑上课时间")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        EditorField("节次名称",label,{label=it},30)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { EditorField("开始",start,{start=it},5,Modifier.weight(1f));EditorField("结束",end,{end=it},5,Modifier.weight(1f)) }
        TextButton(enabled=!model.busy && plan.baseline.periods.size>1,onClick={deleting=true}) { Text("删除这个节次",color=MaterialTheme.colorScheme.error) }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(enabled=!model.busy,onClick={runCatching {
        val periods=plan.baseline.periods.toMutableList().also { it[plan.index]=original.copy(label=label.trim().ifBlank { null },start=start.trim(),end=end.trim()) }
        save(validateSchedule(plan.baseline.copy(periods=periods)))
    }.onFailure { error=it.message ?: "请检查时间" }}) { Text("保存") }},dismissButton={TextButton(enabled=!model.busy,onClick={model.periodEdit=null}) { Text("取消") }})
}
