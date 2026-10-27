package com.dolphin.schedule.ui.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import com.dolphin.schedule.data.NativeRepository
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.notifications.JourneyState
import com.dolphin.schedule.notifications.LiveNotificationSupport
import com.dolphin.schedule.notifications.Notifications
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

/** 权限结果返回后重新核对课程和导航目标；拒绝通知权限仍会打开地图。 */
class NotificationController(
    private val app: Context,private val repository: NativeRepository,private val scope: CoroutineScope,
    private val saved: SavedStateHandle,private val message: (String)->Unit,
    private val openMap: (Intent)->Unit={ app.startActivity(it) },
    private val permissionGranted: ()->Boolean={ Build.VERSION.SDK_INT<33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED },
) {
    private val requests=Channel<Unit>(Channel.CONFLATED)
    val permissions=requests.receiveAsFlow()
    var info by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false);private set
    private var operations=0
    private fun data()=(repository.state.value as? RepositoryState.Ready)?.snapshot?.data ?: error("课表尚未准备好")
    fun permission() {
        if(busy)return
        saved["notificationAction"]=JSONObject().put("kind","enable").toString()
        if(permissionGranted())permissionResult(true) else requests.trySend(Unit)
    }
    fun navigate(course: Course) {
        if(busy)return
        runCatching {
            val current=data();require(current.schedule.courses.find { it.id==course.id }==course) { "课程已变化，请重新打开后导航" }
            val target=courseDestination(course,current.settings)
            saved["notificationAction"]=JSONObject().put("kind","navigate").put("id",course.id)
                .put("title",course.name).put("room",target.room).put("url",target.url).toString()
            if(current.settings.notificationsEnabled && current.settings.journeyLive && !permissionGranted())requests.trySend(Unit)
            else permissionResult(permissionGranted())
        }.onFailure { message(it.message ?: "无法开始课程导航") }
    }
    fun permissionLaunchFailed() {
        // 通知权限的系统页面无法打开时，导航请求仍可继续。
        val navigation=runCatching { JSONObject(saved.get<String>("notificationAction") ?: "{}").optString("kind")=="navigate" }.getOrDefault(false)
        if(navigation)permissionResult(false)
        else { saved.remove<String>("notificationAction");message("系统通知权限暂时无法打开，可在系统设置中开启") }
    }
    fun permissionResult(allowed: Boolean) {
        val raw=saved.remove<String>("notificationAction") ?: return
        operation {
            val action=JSONObject(raw);val current=data()
            when(action.optString("kind")) {
                "navigate" -> {
                    val course=current.schedule.courses.find { it.id==action.getString("id") } ?: error("课程已移除，导航未开始")
                    val target=courseDestination(course,current.settings)
                    require(target.url==action.getString("url") && target.room==action.getString("room") && course.name==action.getString("title")) { "课程或导航设置已变化，请重新点击导航" }
                    try { openMap(Intent(Intent.ACTION_VIEW,Uri.parse(target.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch(_: Exception) { error("未找到地图或浏览器，请先安装可用的地图应用") }
                    if(current.settings.notificationsEnabled && current.settings.journeyLive) {
                        val shown=allowed && withContext(Dispatchers.IO) { Notifications.journey(app,course.id,course.name,target.room,target.url) }
                        if(!shown)message("地图已打开；上课行程未显示，可检查系统通知设置")
                    }
                }
                "enable" -> message(if(allowed)"已允许通知，开始课程导航后显示行程" else "未授予通知权限，课程导航仍可使用")
                // 忽略升级前遗留的测试通知/桌宠操作，不恢复已删除功能。
            }
        }
    }
    fun status()=operation {
        info=withContext(Dispatchers.IO) {
            Notifications.reconcile(app)
            val enabled=app.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            "系统通知${if(enabled)"已允许" else "未允许"}\n${LiveNotificationSupport.describe(app)}"
        }
    }
    fun stopJourney()=operation { withContext(Dispatchers.IO) { Notifications.stopJourney(app) };message("上课行程已结束") }
    fun settings(promoted: Boolean=false) {
        val action=if(promoted && Build.VERSION.SDK_INT>=36)Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS
        runCatching { app.startActivity(Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE,app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { app.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message("无法打开系统通知设置") }
    }
    fun resume()=operation { withContext(Dispatchers.IO) { JourneyState.removeRetiredFeatures(app);Notifications.reconcile(app) } }
    private fun operation(action: suspend ()->Unit) {
        operations++;busy=true
        scope.launch {
            try { action() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { message(failure.message ?: "操作未完成，请重试") }
            finally { operations--;busy=operations>0 }
        }
    }
}
