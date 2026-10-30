package com.dolphin.schedule.academic

/** 主线程内串行协调；用户打开教务浏览时立即让后台读取退出。 */
internal object AcademicSessionGate {
    var interactive=false;private set
    var cancelRead: (()->Unit)?=null
    fun enterInteractive() { cancelRead?.invoke();interactive=true }
    fun leaveInteractive() { interactive=false }
}
