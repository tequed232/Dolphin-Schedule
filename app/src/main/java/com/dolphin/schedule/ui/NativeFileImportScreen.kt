package com.dolphin.schedule.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import com.dolphin.schedule.importing.*

@Composable
fun NativeFileImportScreen(data: AppData,files: FileImportController) {
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(files::select) }
    var paste by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var override by rememberSaveable(files.id,files.suggested) { mutableStateOf(files.suggested.name) }
    val preview=files.preview
    val busy=files.busy || files.saving
    val stale=files.baseline!=null && data.schedule!=files.baseline
    fun choose() { picker.launch(arrayOf("application/json","text/*","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream")) }
    LazyColumn(Modifier.fillMaxSize().testTag("file-import-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(files.id==null && !files.busy) {
            item { Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("选择课表文件",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text("JSON、CSV、XLS 或 XLSX，最大 5 MB。",style=MaterialTheme.typography.bodyMedium)
                Button(onClick={choose()},modifier=Modifier.fillMaxWidth()) { Text("选择文件") }
            } } }
            item { TextButton(onClick={paste=!paste}) { Text(if(paste)"收起 JSON 输入" else "粘贴 JSON 内容") } }
            if(paste)item { Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(files.pasted,files::updatePasted,Modifier.fillMaxWidth(),label={Text("JSON 课表内容")},minLines=6,maxLines=12,shape=RoundedCornerShape(20.dp))
                Button(onClick=files::paste,enabled=files.pasted.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("解析并预览") }
            } }
            item { ImportHelp(data.schedule) }
        } else {
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(files.name,style=MaterialTheme.typography.titleMedium,maxLines=2)
                    files.source?.let { src -> Text(src.format.title+(src.sheets.getOrNull(files.index)?.let { " · ${it.name}" } ?: ""),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(onClick={choose()},enabled=!busy) { Text("重新选择") }
            } }
            if(files.busy)item { LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在本机读取…",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall) }
            if(files.formatIssue)item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("指定文件格式",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ImportFormat.entries.forEach { format -> FilterChip(selected=override==format.name,onClick={override=format.name},label={Text(format.title)}) } }
                Button(onClick={files.overrideFormat(ImportFormat.valueOf(override))},enabled=!busy) { Text("按此格式继续") }
            } }
            val source=files.source
            if(source!=null && !files.reviewing) {
                if(source.sheets.size>1)item { SheetChoice(source,files.index,busy,files::selectSheet) }
                if(source.format!=ImportFormat.JSON && source.sheets.isNotEmpty())item { key(files.id,files.index) { MappingEditor(source.sheets[files.index],files,busy) } }
                if(preview!=null)item {
                    Text(if(preview.onlyConfiguration)"读取到 ${preview.schedule.periods.size} 节作息，已有课程保留。" else "读取到 ${preview.schedule.courses.size} 个课程块。",style=MaterialTheme.typography.titleMedium)
                    Button(onClick=files::showPreview,enabled=!busy,modifier=Modifier.fillMaxWidth().padding(top=12.dp)) { Text("查看导入预览") }
                }
            }
            if(files.reviewing && preview!=null) {
                item { OutlinedButton(onClick={datePicker=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("开学日期 · ${preview.schedule.term.startDate}") } }
                item { SchedulePreviewSummary(files.baseline ?: data.schedule,preview.schedule,showDate=false) }
                if(preview.onlyConfiguration)item { Text("仅更新课表配置，课程与课程 ID 保留。",color=MaterialTheme.colorScheme.primary) }
                if(stale)item { Text("课表已更新，请重新预览后确认。当前文件、工作表和已应用的映射会保留。",color=MaterialTheme.colorScheme.error) }
                item { ImportDetails(preview.schedule) }
                preview.schedule.importNotes.distinct().filterNot { it.startsWith("跳过") }.forEach { note -> item { Text(note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                if(preview.errors.isNotEmpty())item { ErrorDetails(preview.errors) }
                item { Row(Modifier.fillMaxWidth().clickable(enabled=!busy && !stale) { files.acknowledge(!files.acknowledged) },verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(files.acknowledged,files::acknowledge,enabled=!busy && !stale)
                    Text(if(preview.errors.isEmpty())"已核对日期和内容，确认保存。教材、背景和偏好保留。" else "已核对错误明细，同意跳过这些项目并保存其余内容。",style=MaterialTheme.typography.bodyMedium)
                } }
                item { Button(onClick=files::commit,enabled=files.acknowledged && !busy && !stale,modifier=Modifier.fillMaxWidth()) { Text(if(files.saving)"正在保存" else "确认导入") } }
                item { TextButton(onClick=files::showReader,enabled=!busy) { Text("返回修改") } }
            } else if(preview!=null && preview.errors.isNotEmpty())item { ErrorDetails(preview.errors) }
            if(source!=null)item { OutlinedButton(onClick=files::repreview,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("按最新课表重新预览") } }
            item { TextButton(onClick={files.requestDiscard()},enabled=!files.saving,modifier=Modifier.fillMaxWidth()) { Text(if(files.busy)"取消读取" else "放弃此次导入") } }
        }
        files.error?.let { error -> item { Text(error,color=MaterialTheme.colorScheme.error) } }
    }
    if(datePicker && preview!=null)ImportDatePicker(preview.schedule.term.startDate,onDismiss={datePicker=false}) { date -> datePicker=false;files.setDate(date) }
    if(files.discardDialog)AlertDialog(onDismissRequest={files.discardDialog=false},title={Text("放弃此次导入？")},text={Text("未保存的文件和输入会被移除，当前课表保持不变。")},confirmButton={TextButton(onClick=files::confirmDiscard) { Text("放弃导入") }},dismissButton={TextButton(onClick={files.discardDialog=false}) { Text("继续核对") }})
}

@Composable
private fun SheetChoice(source: ImportSource,index: Int,busy: Boolean,onSelect: (Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={expanded=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("工作表 · ${source.sheets.getOrNull(index)?.name ?: "请选择"}") }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) { source.sheets.forEachIndexed { i,sheet -> DropdownMenuItem(text={Text(sheet.name)},onClick={expanded=false;onSelect(i)}) } }
    }
}

@Composable
private fun MappingEditor(sheet: ImportSheet,files: FileImportController,busy: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val guessed=remember(sheet,files.appliedMapping) { files.appliedMapping ?: runCatching { TableImport.mapping(sheet) }.getOrElse { SheetMapping(0,0,1,maxOf(1,sheet.rows.lastIndex),1,minOf(7,(sheet.rows.maxOfOrNull { it.size } ?: 8)-1)) } }
    var fields by rememberSaveable { mutableStateOf(listOf(guessed.dayRow,guessed.sectionColumn,guessed.firstRow,guessed.lastRow,guessed.firstColumn,guessed.lastColumn).map { (it+1).toString() }) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick={expanded=!expanded}) { Text(if(expanded)"收起手动映射" else "手动映射") }
        if(expanded) {
            Text("行列从 1 开始。调整后点击重新解析。",style=MaterialTheme.typography.bodySmall)
            listOf("星期所在行","节次所在列","课程起始行","课程结束行","课程起始列","课程结束列").forEachIndexed { i,label -> EditorField(label,fields[i],{ value -> fields=fields.toMutableList().also { it[i]=value } },4) }
            Button(onClick={runCatching { val n=fields.map { it.toInt()-1 };files.analyzeMapping(SheetMapping(n[0],n[1],n[2],n[3],n[4],n[5]));error=null }.onFailure { error="请填写有效的行列编号" }},enabled=!busy) { Text("按映射重新解析") }
            TextButton(onClick={files.analyzeMapping(null)},enabled=!busy) { Text("恢复自动识别") }
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) { sheet.rows.take(8).forEachIndexed { i,row -> Text("${i+1}  "+row.take(8).joinToString(" ｜ "),style=MaterialTheme.typography.bodySmall,maxLines=2) } } }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ErrorDetails(errors: List<String>) {
    var open by rememberSaveable(errors) { mutableStateOf(false) }
    Column { TextButton(onClick={open=!open}) { Text(if(open)"收起错误明细" else "跳过 ${errors.size} 项 · 查看明细") };if(open)errors.forEach { Text(it,Modifier.padding(vertical=5.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun ImportDetails(schedule: Schedule) {
    var periods by rememberSaveable { mutableStateOf(false) };var courses by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onClick={periods=!periods}) { Text(if(periods)"收起逐节作息" else "展开逐节作息 · ${schedule.periods.size} 节") }
        if(periods)Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { schedule.periods.forEachIndexed { i,p -> Text("第 ${periodLabel(p,i)} 节   ${p.start}—${p.end}");p.breakAfter?.let { Text("${it.label}   ${it.start}—${it.end}",style=MaterialTheme.typography.bodySmall) } } } }
        TextButton(onClick={courses=!courses}) { Text(if(courses)"收起课程预览" else "展开课程预览 · ${schedule.courses.size} 门") }
        if(courses)schedule.courses.forEach { c -> CourseCard(c,schedule,{},Modifier.fillMaxWidth()) }
    }
}
