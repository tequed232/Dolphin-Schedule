package com.dolphin.schedule.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** 仅保存导航行程所需的小型镜像，不保存课表，也不安排课前提醒。 */
object JourneyState {
    fun preferences(context: Context) = context.getSharedPreferences("native", Context.MODE_PRIVATE)
    fun settings(context: Context) = runCatching {
        JSONObject(preferences(context).getString("settings", "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    /** 升级后清除旧闹钟、渠道、台词和封面镜像；不会触碰课程或教材数据库。 */
    fun removeRetiredFeatures(context: Context) {
        listOf(100 to "com.dolphin.schedule.REMIND",213 to Notifications.TIMEOUT_ACTION,
            11 to Notifications.STOP_ACTION,12 to Notifications.DISMISS_ACTION).forEach { (request,action) ->
            val identity=Intent(action).setClassName(context.packageName,"com.dolphin.schedule.ReminderReceiver")
            PendingIntent.getBroadcast(context,request,identity,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                context.getSystemService(AlarmManager::class.java).cancel(it)
                it.cancel()
            }
        }
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("classes-v1")
        manager.deleteNotificationChannel("companion-v1")
        listOf(10,12,99).forEach(manager::cancel)
        val prefs=preferences(context)
        val settings=settings(context)
        listOf("reminders","advance","pet","poke","lines").forEach(settings::remove)
        check(prefs.edit().putString("settings",settings.toString())
            .remove("nextReminder").remove("reminderKeys").remove("reminderToken")
            .remove("schedule").remove("bookCovers").commit())
    }
}

/** 仅接受当前行程 token，过期按钮或旧超时不能结束后来启动的行程。 */
class JourneyReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Notifications.STOP_ACTION,Notifications.DISMISS_ACTION,Notifications.TIMEOUT_ACTION))return
        Notifications.expireJourney(context,intent.getStringExtra("journeyToken") ?: "")
    }
}

/** 安装升级只清理旧功能并核对正在进行的行程，不创建任何新通知。 */
class JourneyChangedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED)return
        val pending=goAsync();val app=context.applicationContext
        Thread {
            try { JourneyState.removeRetiredFeatures(app);Notifications.stopJourney(app) }
            finally { pending.finish() }
        }.start()
    }
}
