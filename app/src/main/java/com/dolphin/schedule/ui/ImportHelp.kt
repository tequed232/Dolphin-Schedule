package com.dolphin.schedule.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.data.AppDataCodec
import com.dolphin.schedule.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ImportHelp(schedule: Schedule) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf("") }
    val template=remember(schedule.term,schedule.periods) {
        val value=AppDataCodec.encodeSchedule(schedule.copy(courses=listOf(Course(name="课程名称",teacher="教师",room="教室",day=1,start=1,end=minOf(2,schedule.periods.size),weeks=(1..schedule.term.weeks).toList())),importNotes=emptyList()))
        value.getJSONArray("courses").getJSONObject(0).remove("id");value.toString(2)
    }
    val csv=remember(schedule.periods) {
        "课程名,星期,节次,上课时间,下课时间,周次,教室,教师,备注\n"+
            schedule.periods.mapIndexed { i,p -> ",,${i+1},${p.start},${p.end},,,," }.joinToString("\n")+
            "\n课程名称,周一,1-${minOf(2,schedule.periods.size)},,,每周,教室,教师,请替换示例内容\n"
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri!=null)scope.launch {
        runCatching { withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "无法写入模板文件" }.use { it.write(pending.toByteArray(Charsets.UTF_8)) } } }.onSuccess { Toast.makeText(context,"模板已导出",Toast.LENGTH_SHORT).show() }.onFailure { error=it.message ?: "模板导出未完成" }
    } }
    TextButton(onClick={open=true}) { Text("模板与导入帮助") }
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("模板与导入帮助")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("JSON 可提供完整学期、节次和课程；缺少的配置沿用当前课表。CSV / Excel 支持课程列表和星期网格，多工作表可自行选择。")
        Text("课程行只填写实际节次、周次和教师地点；不清楚的信息请先核对。连堂时间只更新首尾边界，中间节次不作推算。")
        Text("只导入作息时使用节次、上课时间、下课时间三列。完整覆盖第 1～N 节会将总节数设为 N；部分时间只更新提供的节次。")
        Text("模板带有你当前的日期与作息，请改成真实学期信息后使用。带自定义节次名称或休息安排时优先使用 JSON。")
        OutlinedButton(onClick={pending=template;export.launch("dolphin-schedule-template.json")},modifier=Modifier.fillMaxWidth()) { Text("导出 JSON 完整模板") }
        OutlinedButton(onClick={pending=csv;export.launch("dolphin-schedule-template.csv")},modifier=Modifier.fillMaxWidth()) { Text("导出 CSV 模板") }
        FilledTonalButton(onClick={
            val prompt="请将我提供的课表与逐节作息整理为 Dolphin Schedule JSON。缺少开学日期、完整周次或逐节时间时先问我，不要编造。\n"+
                "保留教师、地点、复杂周次和中午节次。course.day 为星期 1～7，start/end 为 periods 数组中从 1 开始的实际位置；weeks 为实际上课周数数组。\n"+
                "下面仅为格式模板，日期与作息还需核对。请替换示例课程，输出完整 JSON：\n"+template
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("课表整理提示词",prompt));Toast.makeText(context,"提示词已复制",Toast.LENGTH_SHORT).show()
        },modifier=Modifier.fillMaxWidth()) { Text("复制给 AI 的整理提示词") }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(onClick={open=false}) { Text("知道了") }})
}
