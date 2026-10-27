package com.dolphin.schedule.academic

import java.net.URI

object AcademicPolicy {
    const val ORIGIN = "https://jwxt.gdcmxy.edu.cn:1004"
    const val ENTRY = "$ORIGIN/#/jwxt/js/student/personal/studentSchedule"
    const val PROFILE_PREFIX = "dolphin-academic-"
    const val MAX_BYTES = 700_000
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.rawUserInfo == null && (
            uri.host == "jwxt.gdcmxy.edu.cn" && uri.port == 1004 ||
            uri.host == "sso.gdcmxy.edu.cn" && uri.port in listOf(-1,443)
        )
    }.getOrDefault(false)
    fun schedule(url: String) = allowed(url) && runCatching { val uri = URI(url); uri.host == "jwxt.gdcmxy.edu.cn" && uri.port == 1004 && uri.fragment?.substringBefore('?') == "/jwxt/js/student/personal/studentSchedule" }.getOrDefault(false)
    fun destination(input: String): String? {
        val value = input.trim().let { if(it.contains("://")) it else "https://$it" }
        return value.takeIf { it.length <= 2048 && allowed(it) }
    }
    fun requestId(value: String) = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(value)
}
