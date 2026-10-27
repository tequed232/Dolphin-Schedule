package com.dolphin.schedule.ui.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.ui.DolphinViewModel
import com.dolphin.schedule.ui.NativePage
import com.dolphin.schedule.ui.MainTab
import com.dolphin.schedule.ui.navigation.TabSymbol
import com.dolphin.schedule.ui.settings.SettingsIconBadge
import com.dolphin.schedule.ui.timetable.HomeIcon
import com.dolphin.schedule.ui.timetable.HomeSymbol
import kotlin.math.roundToInt

@Composable
fun NativeAppearanceSettings(data: AppData,model: DolphinViewModel) {
    val s=data.settings
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.testTag("appearance-settings")) {
        item { AppearanceLink(NativePage.BACKGROUND,if(data.background!=null && s.backgroundEnabled)data.background.name else "使用主题底色") { model.open(NativePage.BACKGROUND) } }
        item { AppearanceCard {
            Text("深浅模式",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统","light" to "浅色 · 晴日","dark" to "深色 · 夜航").forEach { (mode,label) ->
                FilterChip(s.mode==mode,onClick={model.change { it.copy(settings=it.settings.copy(mode=mode)) }},enabled=!model.busy,label={Text(label)})
            } }
            AppearanceToggle("系统动态颜色",s.dynamicColor,!model.busy) { v -> model.change { it.copy(settings=it.settings.copy(dynamicColor=v)) } }
        } }
        item { AppearanceCard {
            Text("显示大小",style=MaterialTheme.typography.titleMedium)
            var scale by remember(s.scale) { mutableFloatStateOf(s.scale.coerceIn(.7f,1.5f)) }
            Slider(scale,{scale=it},valueRange=.7f..1.5f,steps=15,enabled=!model.busy,modifier=Modifier.testTag("appearance-scale"),onValueChangeFinished={model.change { it.copy(settings=it.settings.copy(scale=scale)) }})
            Text("${(scale*100).roundToInt()}%",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        } }
        item { AppearanceLink(NativePage.SAFE_AREA,"让内容避开状态栏、屏幕切口与手势区") { model.open(NativePage.SAFE_AREA) } }
        item { AppearanceCard {
            Text("性能模式",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("auto" to "自动 · 优先流畅","high" to "完整效果").forEach { (mode,label) ->
                FilterChip(s.performance==mode,onClick={model.change { it.copy(settings=it.settings.copy(performance=mode)) }},enabled=!model.busy,label={Text(label)})
            } }
            Text("自动模式在交互掉帧时降低背景处理开销，课程和文字保持清晰。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

@Composable
fun NativeBackgroundScreen(data: AppData,controller: BackgroundController,reduced: Boolean=false) {
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),controller::selected)
    var remove by remember { mutableStateOf(false) }
    val active=data.background!=null && data.settings.backgroundEnabled
    val blur=controller.previewBlur ?: data.settings.backgroundBlur.coerceIn(0,30)
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.testTag("background-settings")) {
        item { AppearanceCard {
            Box(Modifier.align(Alignment.CenterHorizontally).width(160.dp).height(246.dp).clip(RoundedCornerShape(25.dp))) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {}
                SavedBackground(data,blurOverride=blur,reduced=reduced,tag="background-preview-image")
                Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("背景预览",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("今天",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("让日常，合你的心意。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) { Text("课程与安排",Modifier.fillMaxWidth().padding(12.dp),style=MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.weight(1f))
                    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(9.dp),horizontalArrangement=Arrangement.SpaceBetween) { MainTab.entries.forEach { TabSymbol(it,Modifier.size(17.dp)) } } }
                }
            }
            Text(if(active)"正在使用自定义图片" else "正在使用主题底色",style=MaterialTheme.typography.titleSmall)
            data.background?.let { image -> Text("${image.name} · 原图 ${image.sourceWidth}×${image.sourceHeight}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis) }
            Button(onClick={controller.preparePicker { picker.launch(arrayOf("image/jpeg","image/png","image/webp")) }},enabled=!controller.busy,modifier=Modifier.fillMaxWidth().testTag("background-picker")) { Text(if(controller.busy)"正在处理图片…" else if(data.background==null)"选择背景图片" else "更换背景图片") }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={controller.enabled(false)},enabled=active && !controller.busy) { Text("使用主题底色") }
                if(data.background!=null && !active)OutlinedButton(onClick={controller.enabled(true)},enabled=!controller.busy) { Text("启用这张图片") }
                if(data.background!=null)TextButton(onClick={remove=true},enabled=!controller.busy) { Text("移除图片") }
            }
            if(controller.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            controller.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        } }
        item { AppearanceCard {
            Text("背景毛玻璃",style=MaterialTheme.typography.titleMedium)
            Text(if(!active)"未启用图片" else if(blur==0)"清晰原图" else "$blur px",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Slider(blur.toFloat(),{controller.preview(it.roundToInt())},valueRange=0f..30f,steps=29,enabled=active && !controller.busy,modifier=Modifier.testTag("background-blur"),onValueChangeFinished={controller.saveBlur()})
            Text("拖动预览，松手保存。只模糊背景，课程与文字保持清楚。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Text("JPG / PNG / WebP，单张不超过 10 MB。图片在本机缩小并保存，居中铺满屏幕，不上传。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("移除背景图片？")},text={Text("将移除应用保存的背景，手机相册中的原图保留。")},confirmButton={TextButton(onClick={remove=false;controller.enabled(false,true)},enabled=!controller.busy) { Text("确认移除") }},dismissButton={TextButton(onClick={remove=false}) { Text("取消") }})
}

@Composable
fun NativeSafeAreaSettings(data: AppData,model: DolphinViewModel) {
    val s=data.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("safe-area-settings"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        AppearanceCard {
            AppearanceToggle("上端自动留白",s.topAuto,!model.busy) { v -> model.change { it.copy(settings=it.settings.copy(topAuto=v)) } }
            if(!s.topAuto)InsetSlider("上端留白",s.topInset,!model.busy,"safe-top") { v -> model.change { it.copy(settings=it.settings.copy(topInset=v)) } }
            AppearanceToggle("下端自动留白",s.bottomAuto,!model.busy) { v -> model.change { it.copy(settings=it.settings.copy(bottomAuto=v)) } }
            if(!s.bottomAuto)InsetSlider("下端留白",s.bottomInset,!model.busy,"safe-bottom") { v -> model.change { it.copy(settings=it.settings.copy(bottomInset=v)) } }
        }
        AppearanceCard {
            Text(if(s.topAuto)"顶部 · 自动避让" else "顶部 · ${s.topInset} dp",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Box(Modifier.fillMaxWidth().height(90.dp),contentAlignment=Alignment.Center) { TabSymbol(MainTab.GRID,Modifier.size(36.dp)) }
            Text(if(s.bottomAuto)"底栏与手势区 · 自动避让" else "底栏与手势区 · ${s.bottomInset} dp",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
        Text("自动模式读取系统安全区。手动留白按 dp 调整，横向屏幕切口与键盘仍会自动避让。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsetSlider(label: String,value: Int,enabled: Boolean,tag: String,onSaved: (Int)->Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.coerceIn(0,100).toFloat()) }
    Text("$label ${draft.roundToInt()} dp",style=MaterialTheme.typography.labelLarge)
    Slider(draft,{draft=it},valueRange=0f..100f,steps=99,enabled=enabled,modifier=Modifier.testTag(tag),onValueChangeFinished={onSaved(draft.roundToInt())})
}

@Composable
private fun AppearanceToggle(title: String,value: Boolean,enabled: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Switch(value,onChange,enabled=enabled,modifier=Modifier.semantics { contentDescription=title }) }
}

@Composable
private fun AppearanceLink(page: NativePage,detail: String,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SettingsIconBadge(page)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(page.title,style=MaterialTheme.typography.titleMedium);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis) }
            HomeSymbol(HomeIcon.NEXT,Modifier.size(18.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppearanceCard(content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
}
