package com.dolphin.schedule.ui.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.DolphinViewModel
import com.dolphin.schedule.ui.NativePage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun NativeImportScreen(data: AppData,model: DolphinViewModel,onAcademic: (Boolean)->Unit) {
    val draft=model.academicDraft
    var clear by rememberSaveable { mutableStateOf(false) }
    var undo by remember { mutableStateOf<Pair<Schedule,AcademicHistory>?>(null) }
    val busy=model.busy || model.academicBusy || model.academicPending!=null
    if(draft==null) {
        LazyColumn(Modifier.fillMaxSize().testTag("academic-import-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item { Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("从教务导入",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                    Text("登录学校教务，进入个人学期课表后识别。",style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={onAcademic(false)},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(model.academicPending!=null)"教务页面已打开" else "打开学校教务") }
                }
            } }
            item { OutlinedButton(onClick={model.open(NativePage.FILE_IMPORT)},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("从文件导入 · JSON / CSV / Excel") } }
            item { Text("选择完整学期，取消“大节次”后查询。识别结果会先预览，确认后保存。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(model.academicBusy)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            model.academicError?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
            if(data.academicImport!=null)item {
                OutlinedButton(enabled=!busy,onClick={undo=data.schedule to data.academicImport},modifier=Modifier.fillMaxWidth()) { Text("撤销上次教务导入") }
            }
            item { TextButton(onClick={clear=true},enabled=!busy) { Text("清除本机教务登录") } }
        }
    } else key(draft.id) {
        val initial=draft.confirmedDate ?: draft.snapshot.termStartDate ?: draft.baseline.term.startDate
        var date by rememberSaveable { mutableStateOf(initial) }
        var picker by rememberSaveable { mutableStateOf(false) }
        var showTimes by rememberSaveable { mutableStateOf(false) }
        var showCourses by rememberSaveable { mutableStateOf(false) }
        val activeDate=draft.confirmedDate ?: draft.snapshot.termStartDate
        val dateUnapplied=date!=activeDate
        val preview=model.academicPreview
        val stale=data.schedule!=draft.baseline
        LazyColumn(Modifier.fillMaxSize().testTag("academic-import-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(if(activeDate==null)"先设置开学日期" else "开学日期",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(if(activeDate==null)"教务未提供此学期日期，请先确认。" else if(draft.confirmedDate!=null)"使用你确认的日期" else "来自教务系统的同一学期",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick={picker=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(date) }
                    if(activeDate==null || dateUnapplied)Button(onClick={model.updateAcademicDate(date)},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(if(activeDate==null)"确认日期并预览" else "更新预览") }
                    Text("日期所在周的周一计为第 1 周。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            if(model.academicBusy)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            model.academicError?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
            if(stale)item { Text("当前课表已更新。这份预览不能直接覆盖，请重新识别并核对。",color=MaterialTheme.colorScheme.error) }
            if(preview!=null) {
                item { SchedulePreviewSummary(draft.baseline,preview,showDate=false) }
                preview.importNotes.filterNot { it.startsWith("开学日期：") }.forEach { note -> item { Text(note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                item { TextButton(onClick={showTimes=!showTimes}) { Text(if(showTimes)"收起逐节作息" else "展开逐节作息 · ${preview.periods.size} 节") } }
                if(showTimes)item { Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { preview.periods.forEachIndexed { index,period ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("第 ${periodLabel(period,index)} 节",style=MaterialTheme.typography.bodyMedium);Text("${period.start}—${period.end}",style=MaterialTheme.typography.bodyMedium) }
                    } }
                } }
                item { TextButton(onClick={showCourses=!showCourses}) { Text(if(showCourses)"收起课程预览" else "展开课程预览 · ${preview.courses.size} 门") } }
                if(showCourses)items(preview.courses,key={it.id}) { c ->
                    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(c.name,fontWeight=FontWeight.SemiBold)
                            Text("周${"一二三四五六日"[c.day-1]} · ${coursePeriod(c,preview)}",style=MaterialTheme.typography.bodyMedium)
                            Text("第 ${compactWeeks(c.weeks)} 周",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(listOf(c.teacher,c.room.ifBlank { "地点待补充" }).filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Row(Modifier.fillMaxWidth().clickable(enabled=!busy && !dateUnapplied && !stale) { model.acknowledgeAcademic(!model.academicAcknowledged) }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(model.academicAcknowledged,{model.acknowledgeAcademic(it)},enabled=!busy && !dateUnapplied && !stale)
                    Text("我已核对日期、课程与作息，同意替换当前课表。教材和外观保留。",style=MaterialTheme.typography.bodyMedium)
                } }
                item { Button(onClick=model::applyAcademicImport,enabled=model.academicAcknowledged && !busy && !dateUnapplied && !stale,modifier=Modifier.fillMaxWidth()) { Text(if(model.busy)"正在保存" else "确认导入") } }
            }
            item { TextButton(onClick={model.requestDiscardAcademic()},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("放弃这次识别") } }
        }
        if(picker)ImportDatePicker(date,onDismiss={picker=false}) { selected -> date=selected;picker=false;model.invalidateAcademicAcknowledgment() }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("清除教务登录？")},text={Text("将清除此设备保存的教务登录，下次需要重新登录。课表和教材保留。")},confirmButton={TextButton(onClick={clear=false;onAcademic(true)}) { Text("清除登录") }},dismissButton={TextButton(onClick={clear=false}) { Text("取消") }})
    undo?.let { (baseline,history) -> AlertDialog(onDismissRequest={if(!model.busy)undo=null},title={Text("恢复导入前的课表？")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SchedulePreviewSummary(baseline,history.before)
        Text("导入后的手动课程修改也会被替换。教材、背景和偏好保留。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!model.busy,onClick={model.change("已恢复导入前的课表",expected=baseline,after={undo=null}) {
        require(it.academicImport==history) { "撤销记录已变化，请重新预览" };AcademicImporter.undo(it,baseline)
    }}) { Text("确认恢复课表") }},dismissButton={TextButton(enabled=!model.busy,onClick={undo=null}) { Text("取消") }}) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportDatePicker(date: String,onDismiss: ()->Unit,onSelect: (String)->Unit) {
    val initial=runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    val state=rememberDatePickerState(initialSelectedDateMillis=initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),yearRange=1900..9999)
    DatePickerDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick={state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }}) { Text("确认日期") }},dismissButton={TextButton(onClick=onDismiss) { Text("取消") }}) { DatePicker(state) }
}
