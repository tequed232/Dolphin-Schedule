package com.dolphin.schedule.ui.importing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.academic.AcademicCheckScheduler
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.DolphinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AcademicPendingBanner(onOpen: ()->Unit) {
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
        TextButton(onClick=onOpen,modifier=Modifier.fillMaxWidth()) { Text("有一份教务结果待核对 · 查看") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AcademicCheckSettings(data: AppData,model: DolphinViewModel) {
    val context=LocalContext.current
    val ready=data.academicImport!=null
    val checking=data.academicCheck.status=="checking" && runCatching { Instant.now().epochSecond-Instant.parse(data.academicCheck.checkedAt).epochSecond in 0..89 }.getOrDefault(false)
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("教务更新检查",style=MaterialTheme.typography.titleMedium)
            Text(if(ready)"先在教务页面勾选记住登录。系统会按网络和省电情况安排检查，变化只在 App 内提示。" else "完成一次本校教务导入后，可启用只读周期检查。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(0 to "关闭",6 to "6 小时",12 to "12 小时",24 to "每天").forEach { (hours,label) ->
                    FilterChip(selected=data.settings.academicCheckHours==hours,onClick={model.change { it.copy(settings=it.settings.copy(academicCheckHours=hours)) }},enabled=(ready || hours==0) && !model.busy,label={Text(label)})
                }
            }
            if(data.academicCheck.message.isNotBlank())Text(data.academicCheck.message,style=MaterialTheme.typography.bodyMedium)
            data.academicCheck.checkedAt?.let { at ->
                val date=runCatching { Instant.parse(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) }.getOrDefault("")
                if(date.isNotBlank())Text("最近检查：$date",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick={runCatching { AcademicCheckScheduler.checkNow(context) }.onSuccess { model.message("已请求只读检查，结果会在 App 内提示") }.onFailure { model.error=it.message }},enabled=ready && !checking && !model.busy,modifier=Modifier.fillMaxWidth()) { Text(if(checking)"正在检查" else "立即检查") }
            if(data.pendingAcademic!=null)Text("周期检查暂停等待核对；立即检查只更新候选，不写入当前课表。",style=MaterialTheme.typography.bodySmall)
        }
    }
}
