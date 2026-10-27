package com.dolphin.schedule.maps

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.dolphin.schedule.notifications.Notifications

/** 通知直接启动 Activity；不通过广播/服务跳转，符合 Android 的通知交互约束。 */
class MapLaunchActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token=intent.getStringExtra("journeyToken") ?: ""
        val target=Notifications.currentJourneyMap(this,token)
        if(target==null)Toast.makeText(this,"行程已结束或目标已变化，请重新选择课程",Toast.LENGTH_SHORT).show()
        else runCatching { MapLauncher.open(this,target) }.onFailure {
            Toast.makeText(this,"无法打开地图，请检查地图应用或浏览器",Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
