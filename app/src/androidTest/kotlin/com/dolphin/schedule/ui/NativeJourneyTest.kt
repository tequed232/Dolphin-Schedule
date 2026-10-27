package com.dolphin.schedule.ui

import com.dolphin.schedule.R
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dolphin.schedule.*
import com.dolphin.schedule.data.*
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.notifications.JourneyReceiver
import com.dolphin.schedule.notifications.JourneyState
import com.dolphin.schedule.notifications.Notifications
import com.dolphin.schedule.ui.notifications.NotificationController
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import java.io.File

/** 只在显式授权的隔离模拟器运行；绝不向真机写入合成课表或通知。 */
class NativeJourneyTest {
    @get:Rule(order=0) val emulatorOnly=object: ExternalResource() {
        override fun before() {
            check(InstrumentationRegistry.getArguments().getString("allowNativeDataTests")=="true")
            check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.MODEL.startsWith("sdk_"))
        }
    }
    @get:Rule(order=1) val compose=createAndroidComposeRule<NativeActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    private lateinit var original: AppData
    private lateinit var repository: NativeRepository
    private lateinit var model: DolphinViewModel
    private val scopes=mutableListOf<CoroutineScope>()
    private val messages=mutableListOf<String>()
    private val maps=mutableListOf<Intent>()
    private val first=Course(id="journey-one",name="合成课程甲",room="16栋203号教室",day=1,start=1,end=2,weeks=listOf(1,2))
    private val second=first.copy(id="journey-two",name="合成课程乙",day=2)
    @Before fun prepare() {
        if(Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        Notifications.stopJourney(context);JourneyState.removeRetiredFeatures(context);Notifications.channels(context)
        repository=NativeRepository.get(context)
        runBlocking {
            original=(repository.initialize() as RepositoryState.Ready).snapshot.data
            repository.update { AppData(onboarded=true,schedule=defaultSchedule().copy(courses=listOf(first,second)),
                books=mapOf(first.name to Book(title="合成教材")),settings=Preferences(school="合成学校",dynamicColor=false)) }
        }
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[DolphinViewModel::class.java] }
        compose.waitForIdle()
    }
    @After fun restore() {
        scopes.forEach { runBlocking { it.coroutineContext[Job]?.cancelAndJoin() } }
        Notifications.stopJourney(context);JourneyState.removeRetiredFeatures(context)
        if(::original.isInitialized)runBlocking { repository.update { original } }
    }
    private fun data()=(repository.state.value as RepositoryState.Ready).snapshot.data
    private fun controller(granted: Boolean=false,handle: SavedStateHandle=SavedStateHandle(),failMap: Boolean=false): NotificationController {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also(scopes::add)
        return NotificationController(context,repository,scope,handle,messages::add,openMap={
            if(failMap)throw IllegalStateException("no resolver")
            maps+=it
        },permissionGranted={granted})
    }
    private fun idle(controller: NotificationController)=compose.waitUntil(10_000) { !controller.busy }
    private fun post(course: Course=first,duration: Long=30*60_000L): String {
        val target=courseDestination(course,data().settings)
        assertTrue(Notifications.journey(context,course.id,course.name,target.room,target.url,duration))
        compose.waitUntil(5_000) { Notifications.journeyActive(context) }
        return JourneyState.preferences(context).getString("journeyToken",null)!!
    }
    private fun active()=manager.activeNotifications.first { it.id==Notifications.ACTIVE_JOURNEY_ID }.notification

    @Test fun expandedCardKeepsBrandCourseStatusBookAndTwoActionsWithoutCustomViews() {
        post()
        val notice=active()
        assertEquals(first.name,notice.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(first.name,notice.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString())
        assertEquals("正在前往教室",notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("正在前往教室\n教材：合成教材",notice.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertNotNull(notice.getLargeIcon())
        assertEquals(listOf("去16栋203号教室","我到了"),notice.actions.map { it.title.toString() })
        assertNull(notice.contentView);assertNull(notice.bigContentView);assertNull(notice.headsUpContentView)
        assertFalse(notice.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertTrue(notice.flags and Notification.FLAG_ONGOING_EVENT!=0)
        if(Build.VERSION.SDK_INT>=36)assertTrue(notice.hasPromotableCharacteristics())
        try {
            instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
            SystemClock.sleep(700)
            val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.cacheDir,"journey-standard-expanded.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            image.recycle()
        } finally { instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close() }
    }

    @Test fun settingsOnlyContainNavigationJourneyAndResumeDoesNotCreateNotification() {
        compose.runOnUiThread { model.open(NativePage.NOTIFICATIONS) }
        compose.onNodeWithText("导航行程").assertIsDisplayed()
        listOf("课前提醒","上课提醒","发送测试通知","通知栏桌宠","台词管理","模拟导航课程并检查状态").forEach {
            compose.onNodeWithText(it,substring=true).assertDoesNotExist()
        }
        compose.runOnUiThread { model.notifications.resume() };idle(model.notifications)
        assertFalse(Notifications.journeyActive(context))
        assertFalse(JourneyState.preferences(context).contains("schedule"))
        assertFalse(JourneyState.preferences(context).contains("bookCovers"))
        assertNull(manager.getNotificationChannel("classes-v1"));assertNull(manager.getNotificationChannel("companion-v1"))
    }
    @Test fun refusedOrUnavailablePermissionStillOpensTheCurrentMapWithoutNotification() {
        val controller=controller()
        compose.runOnUiThread { controller.navigate(first);controller.permissionResult(false) };idle(controller)
        assertEquals(courseDestination(first,data().settings).url,maps.single().data.toString())
        assertFalse(Notifications.journeyActive(context));assertTrue(messages.any { it.contains("地图已打开") })
        maps.clear();messages.clear()
        compose.runOnUiThread { controller.navigate(first);controller.permissionLaunchFailed() };idle(controller)
        assertEquals(1,maps.size);assertFalse(Notifications.journeyActive(context))
    }
    @Test fun changedDestinationWhilePermissionDialogIsOpenCannotNavigateTheOldTarget() {
        val handle=SavedStateHandle();val controller=controller(handle=handle)
        compose.runOnUiThread { controller.navigate(first) }
        runBlocking { repository.update { it.copy(settings=it.settings.copy(school="另一所学校")) } }
        compose.runOnUiThread { controller.permissionResult(true) };idle(controller)
        assertTrue(maps.isEmpty());assertFalse(Notifications.journeyActive(context))
        assertTrue(messages.any { it.contains("课程或导航设置已变化") })
        assertFalse(handle.contains("notificationAction"))
    }
    @Test fun mapFailureAndRetiredSavedActionsNeverCreateJourney() {
        val controller=controller(granted=true,failMap=true)
        compose.runOnUiThread { controller.navigate(first) };idle(controller)
        assertFalse(Notifications.journeyActive(context));assertTrue(messages.any { it.contains("未找到地图") })
        for(kind in listOf("test","journey","pet")) {
            val legacy=controller(granted=true,handle=SavedStateHandle(mapOf("notificationAction" to JSONObject().put("kind",kind).toString())))
            compose.runOnUiThread { legacy.permissionResult(true) };idle(legacy)
        }
        assertFalse(Notifications.journeyActive(context));assertTrue(maps.isEmpty())
    }
    @Test fun arrivalActionDirectlyClosesJourneyAndOldTokenCannotCloseANewOne() {
        val old=post();val oldArrival=active().actions[1].actionIntent;val current=post(second)
        assertNotEquals(old,current)
        JourneyReceiver().onReceive(context,Intent(Notifications.TIMEOUT_ACTION).putExtra("journeyToken",old))
        assertTrue(Notifications.journeyActive(context))
        oldArrival.send();compose.waitForIdle()
        assertTrue(Notifications.journeyActive(context))
        val notice=active()
        assertEquals(listOf("去16栋203号教室","我到了"),notice.actions.map { it.title.toString() })
        assertFalse(notice.extras.toString().contains("课前提醒"))
        notice.actions[1].actionIntent.send()
        compose.waitUntil(5_000) { !Notifications.journeyActive(context) }
        assertFalse(JourneyState.preferences(context).contains("activeJourney"))
        assertFalse(JourneyState.preferences(context).contains("journeyToken"))
        assertTrue(manager.activeNotifications.none { it.id in listOf(10,11,12,99) })
    }
    @Test fun resumeKeepsDeadlineAndExpiredOrChangedTargetIsRemovedWithoutRevival() {
        post(duration=Long.MAX_VALUE)
        val prefs=JourneyState.preferences(context)
        val before=JSONObject(prefs.getString("activeJourney",null)!!)
        assertEquals(30*60_000L,before.getLong("endsElapsed")-before.getLong("startedElapsed"))
        Notifications.reconcile(context)
        assertEquals(before.toString(),prefs.getString("activeJourney",null))
        val now=SystemClock.elapsedRealtime()
        before.put("startedElapsed",now-60_000).put("endsElapsed",now-1)
        prefs.edit().putString("activeJourney",before.toString()).commit()
        Notifications.reconcile(context)
        assertFalse(Notifications.journeyActive(context));assertFalse(prefs.contains("activeJourney"))
        post()
        runBlocking { repository.update { it.copy(schedule=it.schedule.copy(courses=it.schedule.courses.map { course -> if(course.id==first.id)course.copy(room="17栋101号教室") else course })) } }
        compose.waitUntil(5_000) { !Notifications.journeyActive(context) }
        Notifications.reconcile(context);assertFalse(Notifications.journeyActive(context))
    }
    @Test fun upgradeCleanupCancelsLegacyAlarmChannelsAndTextWithoutChangingBooksOrCourses() {
        val before=data();val prefs=JourneyState.preferences(context)
        manager.createNotificationChannel(NotificationChannel("classes-v1","旧提醒",NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("companion-v1","旧桌宠",NotificationManager.IMPORTANCE_LOW))
        manager.notify(10,Notification.Builder(context,"companion-v1").setSmallIcon(R.drawable.ic_notification).setContentTitle("合成残留").build())
        val intent=Intent("com.dolphin.schedule.REMIND").setClassName(context.packageName,"com.dolphin.schedule.ReminderReceiver")
        val pending=PendingIntent.getBroadcast(context,100,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+60_000,pending)
        prefs.edit().putString("settings",JSONObject().put("reminders",true).put("advance",10).put("pet",true).put("poke",true).put("lines","旧台词").toString())
            .putLong("nextReminder",100).putString("reminderKeys","[]").putString("reminderToken","retired")
            .putString("bookCovers","{}").putString("schedule","{}").commit()
        JourneyState.removeRetiredFeatures(context)
        assertNull(PendingIntent.getBroadcast(context,100,intent,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        assertNull(manager.getNotificationChannel("classes-v1"));assertNull(manager.getNotificationChannel("companion-v1"))
        listOf("nextReminder","reminderKeys","reminderToken","bookCovers","schedule").forEach { assertFalse(prefs.contains(it)) }
        listOf("reminders","advance","pet","poke","lines").forEach { assertFalse(JourneyState.settings(context).has(it)) }
        assertEquals(before,data())
        assertTrue(manager.activeNotifications.none { it.id==10 })
    }
}
