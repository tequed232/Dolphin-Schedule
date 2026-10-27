package com.dolphin.schedule.notifications



/** 使用单调时钟核对期限，反复返回 App 不延长行程。 */
internal object JourneyPolicy {
    const val MAX_DURATION=30*60_000L
    fun duration(requested: Long)=requested.coerceIn(60_000L,MAX_DURATION)
    fun alive(started: Long,end: Long,now: Long)=started>=0 && end>started &&
        end-started<=MAX_DURATION && now>=started && now<end
}
