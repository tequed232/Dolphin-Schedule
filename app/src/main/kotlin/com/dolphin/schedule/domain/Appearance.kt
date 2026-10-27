package com.dolphin.schedule.domain

import java.nio.ByteBuffer
import java.security.MessageDigest

data class BackgroundState(val image: Background?,val enabled: Boolean)
fun backgroundState(data: AppData)=BackgroundState(data.background,data.settings.backgroundEnabled)

/** 系统选图期间仅保存校验摘要，不把图片塞入 Activity Bundle。 */
fun backgroundFingerprint(state: BackgroundState): String {
    val digest=MessageDigest.getInstance("SHA-256")
    digest.update(if(state.enabled)1.toByte() else 0.toByte())
    digest.update(if(state.image!=null)1.toByte() else 0.toByte())
    state.image?.let { image ->
        listOf(image.url,image.name,image.width.toString(),image.height.toString(),image.sourceWidth.toString(),image.sourceHeight.toString()).forEach { value ->
            val bytes=value.toByteArray(Charsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array());digest.update(bytes)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
fun replaceBackground(data: AppData,fingerprint: String,image: Background): AppData {
    require(backgroundFingerprint(backgroundState(data))==fingerprint) { "背景已变化，请重新选择图片；当前背景保留" }
    return data.copy(background=image,settings=data.settings.copy(backgroundEnabled=true))
}
fun changeBackground(data: AppData,expected: BackgroundState,enabled: Boolean,remove: Boolean=false): AppData {
    require(backgroundState(data)==expected) { "背景已变化，请重新核对后操作" }
    require(!enabled || data.background!=null) { "请先选择背景图片" }
    return data.copy(background=if(remove)null else data.background,settings=data.settings.copy(backgroundEnabled=enabled && !remove))
}
fun changeBackgroundBlur(data: AppData,expected: BackgroundState,previous: Int,value: Int): AppData {
    require(value in 0..30)
    require(backgroundState(data)==expected && data.settings.backgroundBlur==previous) { "背景设置已变化，请重新核对后调整" }
    require(expected.image!=null && expected.enabled) { "请先启用背景图片" }
    return data.copy(settings=data.settings.copy(backgroundBlur=value))
}

fun shouldReduceBackgroundFrames(intervals: List<Long>,refreshRate: Float): Boolean {
    if(intervals.size<24)return false
    val rate=refreshRate.takeIf { it.isFinite() && it in 30f..240f } ?: 60f
    val budget=1_000_000_000.0/rate*1.5
    return intervals.takeLast(24).count { it>budget }>3
}
