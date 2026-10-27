package com.dolphin.schedule.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.dolphin.schedule.R
import org.json.JSONObject

/** 只有主动导航才创建行程通知；不发课前提醒、测试通知或常驻桌宠。 */
object Notifications {
    private const val JOURNEY_CHANNEL="journey-v1"
    private const val JOURNEY_ID=11
    const val ACTIVE_JOURNEY_ID=JOURNEY_ID
    const val STOP_ACTION="com.dolphin.schedule.STOP_JOURNEY"
    const val DISMISS_ACTION="com.dolphin.schedule.DISMISS_JOURNEY"
    const val TIMEOUT_ACTION="com.dolphin.schedule.JOURNEY_TIMEOUT"
    private val colorOSIcon: Boolean
        get()=Build.VERSION.SDK_INT>=36 && listOf(Build.MANUFACTURER,Build.BRAND).any {
            it.equals("oppo",true) || it.equals("realme",true) || it.equals("oneplus",true)
        }

    fun channels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(JOURNEY_CHANNEL,"上课行程",NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="主动导航后显示行程，最长 30 分钟"
            })
    }
    private fun open(context: Context,courseId: String): PendingIntent = PendingIntent.getActivity(context,courseId.hashCode(),
        notificationIntent(context).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("courseId",courseId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun broadcast(context: Context,name: String,token: String)=Intent(context,JourneyReceiver::class.java)
        .setAction(name).setData(Uri.parse("dolphin-schedule://journey/$token")).putExtra("journeyToken",token)
    private fun timeoutIntent(context: Context,token: String) = PendingIntent.getBroadcast(context,213,
        broadcast(context,TIMEOUT_ACTION,token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelTimeout(context: Context,token: String) {
        if(token.isBlank())return
        PendingIntent.getBroadcast(context,213,broadcast(context,TIMEOUT_ACTION,token),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it);it.cancel()
        }
    }
    private fun action(context: Context,name: String,request: Int,token: String) = PendingIntent.getBroadcast(context,request,
        broadcast(context,name,token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun active(context: Context)=runCatching {
        JSONObject(JourneyState.preferences(context).getString("activeJourney","{}") ?: "{}")
    }.getOrDefault(JSONObject())
    private fun roomLabel(context: Context,value: String): String {
        val school=JourneyState.settings(context).optString("school").trim()
        val room=if(school.isNotEmpty() && value.trim().startsWith(school))value.trim().removePrefix(school) else value.trim()
        return room.replace(Regex("^.{2,25}?(?:大学|学院|学校)(?:.{0,12}?校区)?(?=.{1,20}(?:楼|栋|教室))"),"")
            .trim(' ','·','-','—','，',',','：',':').ifBlank { "教室待填写" }
    }
    private fun bookTitle(context: Context,name: String)=runCatching {
        JSONObject(JourneyState.preferences(context).getString("bookTitles","{}") ?: "{}").optString(name)
    }.getOrDefault("")
    private fun targetMatches(context: Context,state: JSONObject): Boolean = runCatching {
        val targets=JSONObject(JourneyState.preferences(context).getString("journeyTargets","{}") ?: "{}")
        val target=targets.getJSONObject(state.getString("courseId"))
        target.getString("title")==state.getString("title") && target.getString("room")==state.getString("sourceRoom") &&
            target.getString("url")==state.getString("mapUrl")
    }.getOrDefault(false)
    private fun withinLifetime(state: JSONObject): Boolean {
        // elapsedRealtime 不受用户修改时钟影响；重启后系统通知消失，reconcile 会直接清理。
        val now=SystemClock.elapsedRealtime();val started=state.optLong("startedElapsed",-1);val end=state.optLong("endsElapsed",-1)
        return JourneyPolicy.alive(started,end,now)
    }
    @Synchronized fun updateMirror(context: Context,settings: JSONObject,books: String,targets: String) {
        check(JourneyState.preferences(context).edit().putString("settings",settings.toString())
            .putString("bookTitles",books).putString("journeyTargets",targets).remove("schedule").remove("bookCovers").commit())
        reconcile(context)
    }
    fun journeyActive(context: Context)=runCatching {
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==JOURNEY_ID }
    }.getOrDefault(false)

    /** 返回前台或数据变更只验证已有行程，从不恢复通知或延长到期时间。 */
    @Synchronized fun reconcile(context: Context) {
        val prefs=JourneyState.preferences(context);val state=active(context);val settings=JourneyState.settings(context)
        if(!settings.optBoolean("notificationsEnabled",true) || !settings.optBoolean("journeyLive",true) ||
            !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() ||
            !journeyActive(context) || !withinLifetime(state) || !targetMatches(context,state)) {
            if(prefs.contains("activeJourney") || prefs.contains("journeyToken") || journeyActive(context))stopJourney(context)
        }
    }
    @Synchronized fun expireJourney(context: Context,token: String) {
        if(token.isNotBlank() && JourneyState.preferences(context).getString("journeyToken",null)==token)stopJourney(context)
    }

    /** 目标必须仍与已提交的课程镜像一致，调用者已成功打开地图后才可调用。 */
    @Synchronized fun journey(context: Context,courseId: String,title: String,room: String,mapUrl: String,timeoutMillis: Long=JourneyPolicy.MAX_DURATION): Boolean {
        val settings=JourneyState.settings(context);val manager=context.getSystemService(NotificationManager::class.java)
        if(!settings.optBoolean("notificationsEnabled",true) || !settings.optBoolean("journeyLive",true) || !manager.areNotificationsEnabled())return false
        val requested=JSONObject().put("courseId",courseId).put("title",title).put("sourceRoom",room).put("mapUrl",mapUrl)
        if(!targetMatches(context,requested))return false
        reconcile(context)
        val existing=active(context)
        if(journeyActive(context) && existing.optString("courseId")==courseId && existing.optString("mapUrl")==mapUrl && withinLifetime(existing))return true
        channels(context)
        if(manager.getNotificationChannel(JOURNEY_CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val duration=JourneyPolicy.duration(timeoutMillis);val token=java.util.UUID.randomUUID().toString()
        val started=System.currentTimeMillis();val elapsed=SystemClock.elapsedRealtime()
        val presentation=courseNotificationPresentation(title,roomLabel(context,room),bookTitle(context,title))
        val map=PendingIntent.getActivity(context,13,Intent(Intent.ACTION_VIEW,Uri.parse(mapUrl)),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(context,JOURNEY_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).journeyCard(context,presentation).setColor(0x3B6550)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(if(Build.VERSION.SDK_INT>=28)Notification.CATEGORY_NAVIGATION else Notification.CATEGORY_SERVICE)
            .setWhen(started).setShowWhen(false).setUsesChronometer(false).setSubText(null)
            .setContentIntent(open(context,courseId)).setTimeoutAfter(duration).setDeleteIntent(action(context,DISMISS_ACTION,12,token))
            .addAction(Notification.Action.Builder(null,presentation.destinationAction,map).build())
            .addAction(Notification.Action.Builder(null,"我到了",action(context,STOP_ACTION,11,token))
                .addExtras(android.os.Bundle().apply { putBoolean("android.support.action.showsUserInterface",false) }).build())
        if(colorOSIcon)builder.setSmallIcon(Icon.createWithResource(context,R.drawable.live_icon))
        if(Build.VERSION.SDK_INT>=36)builder.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing",true) })
            .setShortCriticalText(presentation.collapsedBuilding)
        try { manager.notify(JOURNEY_ID,builder.build()) } catch(_: SecurityException) { return false }
        val prefs=JourneyState.preferences(context);val previousToken=prefs.getString("journeyToken","") ?: ""
        val state=requested.put("startedAt",started).put("endsAt",started+duration).put("startedElapsed",elapsed).put("endsElapsed",elapsed+duration)
        if(!prefs.edit().putString("activeJourney",state.toString()).putString("journeyToken",token).commit()) {
            stopJourney(context);return false
        }
        val alarm=context.getSystemService(AlarmManager::class.java)
        cancelTimeout(context,previousToken)
        // 非精确超时只负责兜底清理；通知本身也设置相同系统超时，无精确闹钟权限。
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,elapsed+duration,timeoutIntent(context,token))
        val app=context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ expireJourney(app,token) },duration)
        return true
    }
    @Synchronized fun stopJourney(context: Context) {
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.cancel(JOURNEY_ID);manager.cancel(12) // 兼容清除旧版额外“已到达”通知。
        cancelTimeout(context,JourneyState.preferences(context).getString("journeyToken","") ?: "")
        JourneyState.preferences(context).edit().remove("activeJourney").remove("journeyToken").apply()
    }
}
