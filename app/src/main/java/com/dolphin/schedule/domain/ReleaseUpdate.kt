package com.dolphin.schedule.domain

import com.dolphin.schedule.UpdateVersion
import org.json.JSONObject
import java.net.URI

const val PROJECT_URL="https://github.com/tequed232/Dolphin-Schedule"
const val RELEASES_URL="$PROJECT_URL/releases"
const val RELEASE_API="https://api.github.com/repos/tequed232/Dolphin-Schedule/releases/latest"

data class ReleaseInfo(
    val version: String,val title: String,val notes: String,val pageUrl: String,
    val publishedAt: String,val assetUrl: String?=null,val assetName: String?=null,
) {
    fun newerThan(installed: String)=UpdateVersion.newer(version,installed)
    val downloadUrl get()=assetUrl ?: pageUrl
}

/** 只接受本项目的正式版本；浏览器负责下载及后续用户确认的安装。 */
fun parseOfficialRelease(raw: String): ReleaseInfo {
    require(raw.toByteArray(Charsets.UTF_8).size<=2_000_000) { "版本信息过大" }
    val value=JSONObject(raw)
    require(!value.optBoolean("draft") && !value.optBoolean("prerelease")) { "这不是正式版本" }
    val version=value.getString("tag_name")
    require(Regex("^[vV]?\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}$").matches(version)) { "版本号格式不支持" }
    val path="/tequed232/Dolphin-Schedule/releases/tag/$version"
    val page=value.getString("html_url")
    require(trustedGithubUrl(page,path)) { "版本页面不属于 Dolphin Schedule" }
    val assets=value.optJSONArray("assets")
    val candidates=(0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }.filter { asset ->
        val name=asset.optString("name")
        val url=asset.optString("browser_download_url")
        name.length in 1..160 && name.endsWith(".apk",true) && !name.contains("debug",true) &&
            name.none { it=='/' || it=='\\' || it.isISOControl() } &&
            trustedGithubAsset(url,version)
    }
    val universal=candidates.filter { it.optString("name").contains("universal",true) }
    val selected=universal.singleOrNull() ?: candidates.singleOrNull()
    return ReleaseInfo(version,value.optString("name").ifBlank { version }.take(240),value.optString("body").take(12_000),page,
        value.optString("published_at").take(80),selected?.getString("browser_download_url"),selected?.getString("name"))
}

private fun trustedGithubUrl(value: String,path: String): Boolean=runCatching {
    val uri=URI(value)
    uri.scheme=="https" && uri.host=="github.com" && uri.rawAuthority=="github.com" &&
        uri.rawQuery==null && uri.rawFragment==null && uri.rawPath==path
}.getOrDefault(false)

private fun trustedGithubAsset(value: String,version: String): Boolean=runCatching {
    val uri=URI(value)
    val prefix="/tequed232/Dolphin-Schedule/releases/download/$version/"
    val name=uri.rawPath?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix).orEmpty()
    trustedGithubUrl(value,prefix+name) && name.isNotBlank() && '/' !in name && '\\' !in name &&
        name !in listOf(".","..") && !Regex("%(2f|5c|2e)",RegexOption.IGNORE_CASE).containsMatchIn(name)
}.getOrDefault(false)
