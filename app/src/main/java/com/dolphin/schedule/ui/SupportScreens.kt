package com.dolphin.schedule.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.BuildConfig
import com.dolphin.schedule.domain.PROJECT_URL
import com.dolphin.schedule.domain.RELEASES_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NativeWelcomeScreen(busy: Boolean,onImport: ()->Unit,onManual: ()->Unit,onContinue: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("native-welcome"),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Dolphin\nSchedule",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
        Text("从容安排每一天",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
        Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("课表留在本机",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text("课程、教材和自选背景保存在这台设备。你可以在设置中导出备份，换机时再恢复。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick=onImport,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("welcome-import")) { Text("导入课表") }
        OutlinedButton(onClick=onManual,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("welcome-manual")) { Text("手动添加课程") }
        TextButton(onClick=onContinue,enabled=!busy,modifier=Modifier.align(Alignment.CenterHorizontally).testTag("welcome-continue")) { Text("先进入课表") }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun NativeUpdateScreen(controller: SupportController,onMessage: (String)->Unit={}) {
    val context=LocalContext.current
    LaunchedEffect(controller) { controller.ensureChecked() }
    LazyColumn(Modifier.fillMaxSize().testTag("native-updates"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { SupportCard {
            Text("Dolphin Schedule",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text("当前版本 ${controller.installedVersion}",Modifier.testTag("update-installed"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            val release=controller.release
            Text(when { controller.busy->"正在检查正式版本";controller.error!=null->"暂时无法检查更新";controller.noRelease->"尚未发布正式版本";release?.newerThan(controller.installedVersion)==true->"发现新版本 ${release.version}";controller.checked->"当前已是最新正式版本";else->"检查最新正式版本" },style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("update-status"))
            if(controller.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            controller.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("update-error"));if(release!=null)Text("下方保留上次成功获取的版本信息。",style=MaterialTheme.typography.bodySmall) }
            controller.checkedAt?.let { time -> Text("上次检查："+DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick=controller::check,enabled=!controller.busy,modifier=Modifier.testTag("update-check")) { Text(if(controller.checked)"重新检查" else "检查更新") }
        } }
        controller.release?.let { release ->
            item { SupportCard {
                Text(release.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text("版本 ${release.version}",color=MaterialTheme.colorScheme.primary)
                if(release.assetName!=null)Text(release.assetName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={openSupportLink(context,release.downloadUrl,onMessage)},modifier=Modifier.fillMaxWidth().testTag("update-download")) { Text(if(release.assetUrl!=null)"在浏览器下载安装包" else "前往版本页面下载") }
                Text("下载和安装由浏览器与系统处理。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { Text("更新内容",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }
            item { SelectionContainer { Text(release.notes.ifBlank { "此版本未填写更新说明。" },modifier=Modifier.testTag("update-notes"),style=MaterialTheme.typography.bodyMedium) } }
        }
        item { TextButton(onClick={openSupportLink(context,RELEASES_URL,onMessage)},modifier=Modifier.testTag("update-releases")) { Text("查看全部正式版本") } }
    }
}

@Composable
fun NativeAboutScreen(onGuide: ()->Unit,onUpdates: ()->Unit,onMessage: (String)->Unit={}) {
    val context=LocalContext.current
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("native-about"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { SupportCard {
            Text("Dolphin Schedule",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
            Text("版本 ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}")
            Text("作者：tequed232",Modifier.testTag("about-author"))
            Text("一款本地课表应用，提供课程导入、课程与教材管理、导航和外观设置。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SupportCard {
            Text("项目与帮助",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            OutlinedButton(onClick=onUpdates,modifier=Modifier.fillMaxWidth().testTag("about-updates")) { Text("检查更新") }
            OutlinedButton(onClick={openSupportLink(context,PROJECT_URL,onMessage)},modifier=Modifier.fillMaxWidth().testTag("about-project")) { Text("GitHub 项目") }
            TextButton(onClick=onGuide,modifier=Modifier.fillMaxWidth().testTag("about-guide")) { Text("重新查看使用引导") }
        } }
        item { Text("开源许可",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }
        items(SupportDocument.entries,key={it.name}) { entry ->
            Surface(onClick={document=entry.name},modifier=Modifier.fillMaxWidth().testTag("about-license-${entry.name}"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(entry.title,style=MaterialTheme.typography.titleMedium);Text(entry.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Text("›") }
            }
        }
        item { Text("课程、教材与偏好保存在本机。教务登录由你自行完成，登录状态不包含在课表备份中。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    document?.let { name -> SupportDocument.entries.find { it.name==name }?.let { SupportDocumentDialog(it) { document=null } } }
}

private enum class SupportDocument(val title: String,val description: String,val asset: String) {
    MIT("MIT 许可证","项目业务代码的使用、修改和分发许可","legal/MIT.txt"),
    THIRD_PARTY("第三方组件与许可","组件来源、用途及版权说明","legal/THIRD_PARTY_NOTICES.md"),
    APACHE("Apache 2.0 许可证","AndroidX、Kotlin 与其他相关组件的许可全文","legal/APACHE-2.0.txt"),
}

@Composable
private fun SupportDocumentDialog(document: SupportDocument,onClose: ()->Unit) {
    val context=LocalContext.current
    val text by produceState<String?>(null,document) {
        val loaded=withContext(Dispatchers.IO) {
            runCatching { context.assets.open(document.asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrElse { "许可正文暂时无法读取。请在 GitHub 项目的 LICENSE、THIRD_PARTY_NOTICES.md 或 legal 目录查看。" }
        }
        // 读取在后台进行，发布正文显式回到主线程，避免初始弹窗快照尚未提交时丢失更新。
        withContext(Dispatchers.Main.immediate) { value=loaded }
    }
    AlertDialog(onDismissRequest=onClose,title={Text(document.title)},text={
        val content=text
        if(content==null)CircularProgressIndicator()
        else SelectionContainer { Text(content,Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()).testTag("license-body"),style=MaterialTheme.typography.bodySmall) }
    },confirmButton={TextButton(onClick=onClose) { Text("关闭") }})
}

@Composable
private fun SupportCard(content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
}

private fun openSupportLink(context: android.content.Context,url: String,onMessage: (String)->Unit) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { onMessage("没有可用的浏览器，请稍后重试") }
}
