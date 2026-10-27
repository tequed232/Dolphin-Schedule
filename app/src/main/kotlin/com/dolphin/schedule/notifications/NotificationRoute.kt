package com.dolphin.schedule.notifications

import android.content.Context
import android.content.Intent
import com.dolphin.schedule.NativeActivity

/** 跟随当前包的实际启动入口，独立原生验收包不会回到已停用的旧界面。 */
internal fun notificationIntent(context: Context): Intent {
    val component=context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
    return if(component!=null)Intent().setComponent(component) else Intent(context,NativeActivity::class.java)
}
