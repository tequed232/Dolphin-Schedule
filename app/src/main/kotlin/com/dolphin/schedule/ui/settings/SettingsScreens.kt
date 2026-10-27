package com.dolphin.schedule.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.BuildConfig
import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.DolphinViewModel
import com.dolphin.schedule.ui.NativePage
import com.dolphin.schedule.ui.appearance.NativeAppearanceSettings
import com.dolphin.schedule.ui.appearance.NativeBackgroundScreen
import com.dolphin.schedule.ui.appearance.NativeSafeAreaSettings
import com.dolphin.schedule.ui.books.NativeBooksScreen
import com.dolphin.schedule.ui.importing.NativeFileImportScreen
import com.dolphin.schedule.ui.importing.NativeImportScreen
import com.dolphin.schedule.ui.notifications.NativeNotificationSettings
import com.dolphin.schedule.ui.support.NativeAboutScreen
import com.dolphin.schedule.ui.support.NativeUpdateScreen
import com.dolphin.schedule.ui.timetable.CourseCard
import com.dolphin.schedule.ui.timetable.EditorField
import com.dolphin.schedule.ui.timetable.HomeIcon
import com.dolphin.schedule.ui.timetable.HomeSymbol
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NativeSettingsScreen(data: AppData,model: DolphinViewModel,bottomClearance: androidx.compose.ui.unit.Dp=0.dp) {
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=20.dp+bottomClearance),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("设置",Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold) }
        item { Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(data.schedule.term.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text("${data.schedule.courses.size} 门课程 · ${data.schedule.term.weeks} 周 · ${data.schedule.periods.size} 节",style=MaterialTheme.typography.bodyMedium)
        } } }
        item { SettingsGroup("课表",listOf(NativePage.IMPORT,NativePage.MANAGE,NativePage.TERM,NativePage.TIMES,NativePage.BOOKS),model) }
        item { SettingsGroup("显示",listOf(NativePage.HOME,NativePage.APPEARANCE,NativePage.NAVIGATION),model) }
        item { SettingsGroup("应用",listOf(NativePage.NOTIFICATIONS,NativePage.UPDATES,NativePage.DATA,NativePage.ABOUT),model) }
    }
}

@Composable
private fun SettingsGroup(title: String,pages: List<NativePage>,model: DolphinViewModel) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,Modifier.padding(start=10.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) { Column { pages.forEachIndexed { index,page ->
            Row(Modifier.fillMaxWidth().clickable { model.open(page) }.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                SettingsIconBadge(page)
                Text(page.title,Modifier.weight(1f))
                HomeSymbol(HomeIcon.NEXT,Modifier.size(18.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(index<pages.lastIndex)HorizontalDivider(Modifier.padding(start=70.dp,end=20.dp),color=MaterialTheme.colorScheme.outlineVariant)
        } } }
    }
}

@Composable
fun NativeSettingsPage(page: NativePage,data: AppData,model: DolphinViewModel,onAcademic: (Boolean)->Unit={},reducedBackground: Boolean=false) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={model.back()}) { Text("‹ 返回") };Text(page.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) }
        when(page) {
            NativePage.IMPORT -> NativeImportScreen(data,model,onAcademic)
            NativePage.FILE_IMPORT -> NativeFileImportScreen(data,model.files)
            NativePage.BOOKS -> NativeBooksScreen(data,model)
            NativePage.BACKGROUND -> NativeBackgroundScreen(data,model.background,reducedBackground)
            NativePage.SAFE_AREA -> NativeSafeAreaSettings(data,model)
            NativePage.MANAGE -> LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item { Button(onClick={model.startEdit(data)},modifier=Modifier.fillMaxWidth()) { Text("添加课程") } }
                items(data.schedule.courses.sortedWith(compareBy<Course>{it.day}.thenBy { it.start }.thenBy { it.name }),key={it.id}) { c -> CourseCard(c,data.schedule,{model.show(c)},Modifier.fillMaxWidth()) }
                if(data.schedule.courses.isEmpty())item { Text("还没有课程",Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            NativePage.TERM -> TermSettings(data,model)
            NativePage.TIMES -> TimeSettings(data,model)
            NativePage.HOME -> SettingsContent {
                ToggleSetting("显示周末",data.settings.showWeekend) { v -> model.change { it.copy(settings=it.settings.copy(showWeekend=v)) } }
                ToggleSetting("显示上课时间",data.settings.showTimes) { v -> model.change { it.copy(settings=it.settings.copy(showTimes=v)) } }
                ToggleSetting("当前时间线",data.settings.showCurrentTimeLine) { v -> model.change { it.copy(settings=it.settings.copy(showCurrentTimeLine=v)) } }
                ToggleSetting("导航课程按钮",data.settings.showNavigation) { v -> model.change { it.copy(settings=it.settings.copy(showNavigation=v)) } }
            }
            NativePage.APPEARANCE -> NativeAppearanceSettings(data,model)
            NativePage.NAVIGATION -> SchoolSettings(data,model)
            NativePage.NOTIFICATIONS -> NativeNotificationSettings(data,model)
            NativePage.DATA -> BackupSettings(data,model)
            NativePage.UPDATES -> NativeUpdateScreen(model.support,model::message)
            NativePage.ABOUT -> NativeAboutScreen(model::openGuide,{model.open(NativePage.UPDATES)},model::message)
        }
    }
}

@Composable
internal fun SettingsContent(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}

@Composable
internal fun ToggleSetting(label: String,value: Boolean,onChange: (Boolean)->Unit) {
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));Switch(value,onChange) } }
}

@Composable
private fun TermSettings(data: AppData,model: DolphinViewModel) {
    val baseline=remember { data.schedule }
    var name by rememberSaveable { mutableStateOf(baseline.term.name) }
    var date by rememberSaveable { mutableStateOf(baseline.term.startDate) }
    var weeks by rememberSaveable { mutableStateOf(baseline.term.weeks.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsContent {
        EditorField("开学日期 · YYYY-MM-DD",date,{date=it},10)
        Text("开学日期所在周的周一计为第 1 周。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        EditorField("课表名称",name,{name=it},300)
        EditorField("学期周数",weeks,{weeks=it},3)
        Text("课程已有周次会保留；缩短学期不会删除课程。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(enabled=!model.busy,onClick={runCatching {
            val term=Term(name.trim(),LocalDate.parse(date.trim()).toString(),weeks.toInt())
            validateSchedule(baseline.copy(term=term));error=null
            model.change("学期已保存",expected=baseline,after={model.back()}) { it.copy(schedule=it.schedule.copy(term=term)) }
        }.onFailure { error=it.message ?: "请检查学期信息" }}) { Text("保存学期") }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun TimeSettings(data: AppData,model: DolphinViewModel) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("点击时间编辑或删除；午休占位自动收起。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(data.schedule.periods.indices.toList(),key={it}) { index ->
            val period=data.schedule.periods[index]
            Surface(onClick={model.openPeriod(data.schedule,index)},shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("第 "+periodLabel(period,index)+" 节",style=MaterialTheme.typography.titleMedium);Text(period.start+"—"+period.end,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("编辑",color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { OutlinedButton(enabled=!model.busy && data.schedule.periods.size<MAX_PERIODS,onClick={model.change("已添加节次",expected=data.schedule) { it.copy(schedule=it.schedule.copy(periods=it.schedule.periods+Period("",""))) }}) { Text("添加节次") } }
    }
}

@Composable
private fun SchoolSettings(data: AppData,model: DolphinViewModel) {
    var school by rememberSaveable { mutableStateOf(data.settings.school) }
    SettingsContent {
        EditorField("学校名称",school,{school=it},300)
        Button(enabled=!model.busy,onClick={model.change("学校已保存") { it.copy(settings=it.settings.copy(school=school.trim())) }}) { Text("保存学校") }
        Text("地图应用",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("amap" to "高德地图","baidu" to "百度地图").forEach { (map,label) -> FilterChip(selected=data.settings.map==map,onClick={model.change { it.copy(settings=it.settings.copy(map=map)) }},label={Text(label)}) } }
        Text("地图只搜索学校和楼栋，完整教室号留在课表和通知中。仅在点击导航时打开地图；校内建筑是否可定位取决于地图收录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackupSettings(data: AppData,model: DolphinViewModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var pending by remember { mutableStateOf<AppData?>(null) }
    var expected by remember { mutableStateOf<AppData?>(null) }
    var working by remember { mutableStateOf(false) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)scope.launch {
        working=true
        runCatching { withContext(Dispatchers.IO) { val snapshot=AppDataCodec.encode(data);requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "无法写入备份" }.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } } }.onSuccess { model.message("备份已导出") }.onFailure { model.error=it.message ?: "备份导出失败" }
        working=false
    } }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch {
        working=true
        runCatching { withContext(Dispatchers.IO) {
            val bytes=requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0)break;require(output.size()+n<=AppDataCodec.MAX_BYTES) { "备份超过 16 MB" };output.write(buffer,0,n) }
                output.toByteArray()
            }
            AppDataCodec.decode(bytes.toString(Charsets.UTF_8))
        } }.onSuccess { pending=it;expected=data }.onFailure { model.error=it.message ?: "备份无法识别，原数据保留" }
        working=false
    } }
    SettingsContent {
        Text("备份包含课表、教材、自选背景与偏好。登录状态不包含在备份中。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(enabled=!working && !model.busy,onClick={export.launch("Dolphin-Schedule-${LocalDate.now()}.json")}) { Text("导出完整备份") }
        OutlinedButton(enabled=!working && !model.busy,onClick={restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))}) { Text("从备份恢复") }
        if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    pending?.let { candidate -> AlertDialog(onDismissRequest={if(!model.busy)pending=null},title={Text("恢复这份备份？")},text={Text("${candidate.schedule.term.name}\n${candidate.schedule.courses.size} 门课程 · ${candidate.books.size} 份教材\n\n当前课表、教材与偏好会被替换，请先导出当前备份。")},confirmButton={TextButton(enabled=!model.busy,onClick={model.change("备份已恢复",after={pending=null}) { current -> require(current==expected) { "当前数据已变化，请重新选择备份核对；已保存的数据保留" };candidate }}) { Text("确认恢复") }},dismissButton={TextButton(enabled=!model.busy,onClick={pending=null}) { Text("取消") }}) }
}
