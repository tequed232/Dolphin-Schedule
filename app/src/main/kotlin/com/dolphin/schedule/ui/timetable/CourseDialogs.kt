package com.dolphin.schedule.ui.timetable

import com.dolphin.schedule.ui.navigation.rememberPredictivePageBack
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.DolphinViewModel
import com.dolphin.schedule.ui.EditPlan
import com.dolphin.schedule.ui.books.BookCard
import com.dolphin.schedule.ui.common.SystemBarAppearance
import com.dolphin.schedule.ui.common.dolphinInsets
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetails(course: Course,data: AppData,model: DolphinViewModel) {
    var deleting by rememberSaveable(course.id) { mutableStateOf(false) }
    // 详情内容较长时直接展开，让滚动区域与屏幕内的实际可用高度一致。
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(onDismissRequest={model.detailId=null},sheetState=sheetState,containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(course.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
            DetailLine("时间",coursePeriod(course,data.schedule))
            val first=data.schedule.periods.getOrNull(course.start-1);val last=data.schedule.periods.getOrNull(course.end-1)
            if(first!=null && last!=null)DetailLine("作息","${first.start}—${last.end}")
            DetailLine("日期",course.specificDate ?: "周${"一二三四五六日"[course.day-1]} · 第 ${compactWeeks(course.weeks).ifBlank { "无" }} 周")
            if(course.teacher.isNotBlank())DetailLine("教师",course.teacher)
            if(course.room.isNotBlank())DetailLine("地点",course.room)
            val destination=remember(course.room) { navigationBuilding(course.room) }
            if(destination.isNotBlank()) {
                OutlinedButton(onClick={model.navigate(course)},enabled=!model.notifications.busy,modifier=Modifier.fillMaxWidth()) { Text("导航至$destination") }
                Text("导航到楼栋入口，完整教室号留在课表中。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else OutlinedButton(onClick={model.startEdit(data,course)},modifier=Modifier.fillMaxWidth()) { Text(if(course.room.isBlank())"填写上课地点" else "补全楼栋信息") }
            if(course.notes.isNotBlank())DetailLine("备注",course.notes)
            Text("这门课的教材",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            courseBook(data,course)?.let { book ->
                BookCard(book,Modifier.fillMaxWidth(),onClick={model.editBook(data,course)})
                TextButton(onClick={model.editBook(data,course)}) { Text("编辑教材") }
            } ?: OutlinedButton(onClick={model.editBook(data,course)},modifier=Modifier.fillMaxWidth()) { Text("填写教材与封面") }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick={model.startEdit(data,course)},modifier=Modifier.weight(1f)) { Text("编辑课程") }
                OutlinedButton(onClick={deleting=true},enabled=!model.busy) { Text("删除",color=MaterialTheme.colorScheme.error) }
            }
        }
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("删除这门课程？")},text={Text(course.name)},confirmButton={TextButton(enabled=!model.busy,onClick={model.deleteCourse(course);deleting=false}) { Text("删除") }},dismissButton={TextButton(onClick={deleting=false}) { Text("取消") }})
}

@Composable
private fun DetailLine(label: String,value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.width(40.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CourseEditor(plan: EditPlan,data: AppData,model: DolphinViewModel) {
    val insets=dolphinInsets(data.settings)
    val seed=plan.seed
    var name by rememberSaveable { mutableStateOf(seed.name) }
    var teacher by rememberSaveable { mutableStateOf(seed.teacher) }
    var room by rememberSaveable { mutableStateOf(seed.room) }
    var notes by rememberSaveable { mutableStateOf(seed.notes) }
    var day by rememberSaveable { mutableIntStateOf(seed.day) }
    var start by rememberSaveable { mutableIntStateOf(seed.start) }
    var end by rememberSaveable { mutableIntStateOf(seed.end) }
    var weeks by rememberSaveable { mutableStateOf(compactWeeks(seed.weeks)) }
    var temporary by rememberSaveable { mutableStateOf(seed.temporary) }
    var date by rememberSaveable { mutableStateOf(seed.specificDate ?: model.selectedDate.toString()) }
    var color by rememberSaveable { mutableStateOf(seed.color) }
    var fieldError by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val dirty=name!=seed.name || teacher!=seed.teacher || room!=seed.room || notes!=seed.notes || day!=seed.day || start!=seed.start || end!=seed.end || weeks!=compactWeeks(seed.weeks) || temporary!=seed.temporary || color!=seed.color || (temporary && date!=seed.specificDate)
    fun close() { if(model.busy)return;if(dirty)discard=true else model.cancelEdit() }
    Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,dismissOnBackPress=false)) {
        SystemBarAppearance()
        val predictiveBack=rememberPredictivePageBack(enabled=true,route=plan.seed.id) { close() }
        Scaffold(
            modifier=Modifier.fillMaxSize().imePadding().then(predictiveBack),containerColor=MaterialTheme.colorScheme.background,
            contentWindowInsets=insets,
            topBar={Row(Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={close()},enabled=!model.busy) { Text("取消") }
                Text(if(plan.original==null)"添加课程" else "编辑课程",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Button(enabled=!model.busy,onClick={
                    runCatching {
                        val parsedDate=if(temporary) LocalDate.parse(date.trim()).also { require(it.year in 1900..9999) { "日期超出范围" } } else null
                        val parsedWeeks=if(temporary)listOf(weekOf(parsedDate!!,data.schedule.term.startDate)).also { require(it.single() in 1..999) { "指定日期应在开学后的第 1～999 周内" } }
                            else if(weeks.isBlank() && seed.weeks.isEmpty())emptyList() else parseWeeks(weeks,maxOf(data.schedule.term.weeks,seed.weeks.maxOrNull() ?: 1))
                        val updated=seed.copy(name=name.trim(),teacher=teacher.trim(),room=room.trim(),notes=notes.trim(),day=parsedDate?.dayOfWeek?.value ?: day,start=start,end=end,weeks=parsedWeeks,color=color,temporary=temporary,specificDate=parsedDate?.toString())
                        validateSchedule(data.schedule.copy(courses=listOf(updated)))
                        fieldError=null;model.saveCourse(updated)
                    }.onFailure { fieldError=it.message ?: "请检查课程信息" }
                }) { Text(if(model.busy)"保存中" else "保存") }
            } },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                EditorField("课程名称",name,{name=it},300)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(teacher,{teacher=it.take(500)},Modifier.weight(1f),label={Text("教师")},singleLine=true,shape=RoundedCornerShape(16.dp))
                    OutlinedTextField(room,{room=it.take(500)},Modifier.weight(1f),label={Text("教室")},singleLine=true,shape=RoundedCornerShape(16.dp))
                }
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("指定日期",style=MaterialTheme.typography.titleSmall);Text("只在选定的一天上课",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Switch(checked=temporary,onCheckedChange={temporary=it}) }
                }
                if(temporary)EditorField("上课日期 · YYYY-MM-DD",date,{date=it},10)
                else {
                    Text("上课星期",style=MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        (1..7).forEach { value -> Surface(selected=day==value,onClick={day=value},modifier=Modifier.sizeIn(minWidth=40.dp,minHeight=48.dp),shape=CircleShape,color=if(day==value)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Box(contentAlignment=Alignment.Center) { Text("一二三四五六日"[value-1].toString()) }
                        } }
                    }
                    EditorField("上课周次",weeks,{weeks=it},3000)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("整学期" to "每周","单周" to "单周","双周" to "双周").forEach { (label,value) -> AssistChip(onClick={weeks=value},label={Text(label)}) }
                    }
                    Text("可填写 1-8;10;12-18，或 1-19单",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PeriodMenu("开始节次",start,data.schedule,Modifier.weight(1f)) { start=it;if(end<it)end=it }
                    PeriodMenu("结束节次",end,data.schedule,Modifier.weight(1f)) { end=it;if(start>it)start=it }
                }
                Text("课程颜色",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { COURSE_COLORS.forEachIndexed { index,value ->
                    val palette=coursePalette(value)
                    Surface(selected=color==value,onClick={color=value},modifier=Modifier.size(48.dp).semantics { contentDescription=listOf("鼠尾草绿","薰衣草紫","蜜桃橙","雾蓝","玫瑰粉")[index] },color=palette.first,contentColor=palette.second,shape=CircleShape,border=if(color==value)BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) { Box(contentAlignment=Alignment.Center) { if(color==value)Text("✓") } }
                } }
                OutlinedTextField(notes,{notes=it.take(10000)},Modifier.fillMaxWidth(),label={Text("备注")},minLines=3,shape=RoundedCornerShape(16.dp))
                fieldError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        }
        if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃这次修改？")},text={Text("尚未保存的内容会被丢弃。")},confirmButton={TextButton(onClick={discard=false;model.cancelEdit()}) { Text("放弃修改") }},dismissButton={TextButton(onClick={discard=false}) { Text("继续编辑") }})
    }
}

@Composable
internal fun EditorField(label: String,value: String,onChange: (String)->Unit,limit: Int,modifier: Modifier=Modifier) {
    OutlinedTextField(value,{onChange(it.take(limit))},modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(16.dp))
}

@Composable
private fun PeriodMenu(label: String,value: Int,schedule: Schedule,modifier: Modifier,onSelect: (Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(14.dp)) {
            Column { Text(label,style=MaterialTheme.typography.labelMedium);Text(schedule.periods.getOrNull(value-1)?.let { "第 ${periodLabel(it,value-1)} 节" } ?: "第 $value 节",style=MaterialTheme.typography.titleSmall) }
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) { schedule.periods.forEachIndexed { index,p -> DropdownMenuItem(text={Text("第 ${periodLabel(p,index)} 节 · ${p.start}")},onClick={onSelect(index+1);expanded=false}) } }
    }
}
