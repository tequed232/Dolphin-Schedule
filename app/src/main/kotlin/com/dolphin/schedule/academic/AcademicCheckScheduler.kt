package com.dolphin.schedule.academic

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.webkit.WebViewFeature
import com.dolphin.schedule.domain.AppData
import java.util.concurrent.TimeUnit

object AcademicCheckScheduler {
    const val PERIODIC_ID=4301
    const val MANUAL_ID=4302
    private fun component(context: Context): ComponentName {
        val legacy=AcademicPolicy.useLegacyBrowser(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE),Build.VERSION.SDK_INT)
        return ComponentName(context,if(legacy)LegacyAcademicCheckJobService::class.java else AcademicCheckJobService::class.java)
    }
    fun sync(context: Context,data: AppData) {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        val hours=data.settings.academicCheckHours
        if(hours==0 || data.academicImport==null) { scheduler.cancel(PERIODIC_ID);return }
        require(hours in listOf(6,12,24)) { "教务检查频率无效" }
        val target=component(context);val interval=TimeUnit.HOURS.toMillis(hours.toLong())
        val old=scheduler.getPendingJob(PERIODIC_ID)
        if(old?.service==target && old.intervalMillis==interval && old.isPersisted)return
        val job=JobInfo.Builder(PERIODIC_ID,target).setPeriodic(interval,TimeUnit.HOURS.toMillis(1))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true).setPersisted(true).build()
        check(scheduler.schedule(job)==JobScheduler.RESULT_SUCCESS) { "系统暂未接受周期教务检查，请稍后重试" }
    }
    fun checkNow(context: Context) {
        val job=JobInfo.Builder(MANUAL_ID,component(context)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setExtras(PersistableBundle().apply { putBoolean("manual",true) }).build()
        check(context.getSystemService(JobScheduler::class.java).schedule(job)==JobScheduler.RESULT_SUCCESS) { "系统暂未接受检查，请稍后重试" }
    }
}
