package com.dolphin.schedule

import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dolphin.schedule.academic.AcademicActivity
import com.dolphin.schedule.academic.AcademicPolicy
import com.dolphin.schedule.academic.LegacyAcademicActivity
import androidx.webkit.WebViewFeature
import com.dolphin.schedule.data.RepositoryState
import com.dolphin.schedule.notifications.Notifications
import com.dolphin.schedule.ui.DolphinApp
import com.dolphin.schedule.ui.DolphinViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 正式启动入口；教务浏览通过独立 Activity 返回识别结果。 */
class NativeActivity: ComponentActivity() {
    private val model: DolphinViewModel by viewModels()
    private val academic=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.academicResult(it.resultCode,it.data) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.channels(this)
        enableEdgeToEdge()
        setContent { DolphinApp(model,onExit={finish()},onAcademic={ clear ->
            model.prepareAcademicRequest(clear)?.let { id ->
                try {
                    val multiProfile=WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
                    val target=if(AcademicPolicy.useLegacyBrowser(multiProfile,Build.VERSION.SDK_INT))LegacyAcademicActivity::class.java else AcademicActivity::class.java
                    academic.launch(Intent(this,target).putExtra("requestId",id).putExtra("clearOnly",clear))
                }
                catch(_: Exception) { model.academicLaunchFailed() }
            }
        }) }
        handleIntent(intent)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { model.state.filterIsInstance<RepositoryState.Ready>().first();model.notifications.resume() } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);handleIntent(intent) }
    private fun handleIntent(source: Intent) {
        val id=source.getStringExtra("courseId")
        source.removeExtra("courseId")
        if(id.isNullOrBlank())return
        lifecycleScope.launch {
            val data=model.state.filterIsInstance<RepositoryState.Ready>().first().snapshot.data
            if(!id.isNullOrBlank()) {
                val course=data.schedule.courses.find { it.id==id }
                if(course==null)model.message("这门课程已从课表移除，请重新选择")
                else model.show(course)
            }
        }
    }
}
