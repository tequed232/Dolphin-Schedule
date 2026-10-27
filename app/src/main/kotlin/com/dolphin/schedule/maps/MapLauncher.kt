package com.dolphin.schedule.maps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dolphin.schedule.domain.mapLaunchPlan

/** 课程入口与通知按钮共用：先指定高德客户端，无法启动再打开原有网页。 */
object MapLauncher {
    fun open(context: Context,webUrl: String,launch: (Intent)->Unit={context.startActivity(it)}) {
        val plan=mapLaunchPlan(webUrl)
        fun intent(url: String)=Intent(Intent.ACTION_VIEW,Uri.parse(url))
            .addCategory(Intent.CATEGORY_DEFAULT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(plan.nativeUrl!=null) {
            try {
                launch(intent(plan.nativeUrl).setPackage(plan.nativePackage))
                return
            } catch(_: ActivityNotFoundException) {
                // 没有安装客户端，或当前版本没有对应入口。
            } catch(_: SecurityException) {
                // 厂商禁止客户端跳转时仍可交给网页处理。
            }
        }
        launch(intent(plan.webUrl))
    }
}
