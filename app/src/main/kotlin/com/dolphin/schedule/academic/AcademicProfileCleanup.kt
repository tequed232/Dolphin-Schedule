package com.dolphin.schedule.academic



/** 删除被引擎占用的配置可能失败；清理其站点数据后延后删除，不能阻止新会话。 */
object AcademicProfileCleanup {
    fun clean(names: List<String>, keep: String?, retired: Set<String>, delete: (String) -> Boolean, defer: (String) -> Unit) {
        names.filter { it.startsWith(AcademicPolicy.PROFILE_PREFIX) && it != keep && it !in retired }.forEach { name ->
            try { delete(name) } catch(_: IllegalStateException) { defer(name) }
        }
    }
}
