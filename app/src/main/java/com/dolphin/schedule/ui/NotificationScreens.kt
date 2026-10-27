package com.dolphin.schedule.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dolphin.schedule.domain.AppData

@Composable
internal fun NativeNotificationPermission(controller: NotificationController) {
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(),controller::permissionResult)
    LaunchedEffect(controller) { controller.permissions.collect {
        try { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        catch(_: Exception) { controller.permissionLaunchFailed() }
    } }
}

@Composable
internal fun NativeNotificationSettings(data: AppData,model: DolphinViewModel) {
    val controller=model.notifications
    val enabled=data.settings.notificationsEnabled && data.settings.journeyLive
    SettingsContent {
        ToggleSetting("导航行程",enabled) { value ->
            model.change(after={if(value)controller.permission()}) {
                it.copy(settings=it.settings.copy(notificationsEnabled=value,journeyLive=value))
            }
        }
        Text("开始课程导航后显示灵动岛或行程通知，最长 30 分钟；点“我到了”直接收起。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=controller::status,enabled=!controller.busy) { Text("查看当前行程状态") }
        if(Build.VERSION.SDK_INT>=36)TextButton(onClick={controller.settings(true)}) { Text("系统实时活动设置") }
        TextButton(onClick=controller::stopJourney,enabled=!controller.busy) { Text("结束当前行程") }
        TextButton(onClick={controller.settings()}) { Text("系统通知设置") }
    }
}
