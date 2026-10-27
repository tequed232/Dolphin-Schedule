package com.dolphin.schedule.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateTest {
    private fun raw(tag: String="v1.6.0",assets: List<Pair<String,String>> = emptyList()): JSONObject=JSONObject()
        .put("tag_name",tag).put("name","原生重建").put("body","课程与教材说明")
        .put("draft",false).put("prerelease",false).put("published_at","2026-10-26T08:00:00Z")
        .put("html_url","$RELEASES_URL/tag/$tag")
        .put("assets",JSONArray(assets.map { (name,url) -> JSONObject().put("name",name).put("browser_download_url",url) }))
    private fun apk(name: String,tag: String="v1.6.0")=name to "$RELEASES_URL/download/$tag/$name"

    @Test fun officialReleaseShowsNotesAndPrefersOneUniversalApk() {
        val value=parseOfficialRelease(raw(assets=listOf(apk("arm64.apk"),apk("Dolphin-universal.apk"))).toString())
        assertEquals("v1.6.0",value.version)
        assertEquals("课程与教材说明",value.notes)
        assertEquals("Dolphin-universal.apk",value.assetName)
        assertTrue(value.newerThan("1.5.5"))
        assertFalse(value.newerThan("1.6.0"))
        assertFalse(value.newerThan("2.0.0"))
    }
    @Test fun ambiguousMissingOrDebugAssetsUseVersionPageWithoutGuessing() {
        listOf(emptyList(),listOf(apk("arm64.apk"),apk("x86.apk")),listOf(apk("debug.apk")),listOf(apk("a-universal.apk"),apk("b-universal.apk"))).forEach { assets ->
            val value=parseOfficialRelease(raw(assets=assets).toString())
            assertNull(value.assetUrl);assertEquals(value.pageUrl,value.downloadUrl)
        }
        assertEquals("one.apk",parseOfficialRelease(raw(assets=listOf(apk("one.apk"))).toString()).assetName)
    }
    @Test fun previewVersionsDraftsAndForeignOrDecoratedPagesAreRejected() {
        listOf("v1.6.0-beta","main","1.6","v1.6.0 ").forEach { tag -> assertThrows(IllegalArgumentException::class.java) { parseOfficialRelease(raw(tag).toString()) } }
        listOf("draft","prerelease").forEach { field -> assertThrows(IllegalArgumentException::class.java) { parseOfficialRelease(raw().put(field,true).toString()) } }
        listOf("https://github.com/another/Repo/releases/tag/v1.6.0","https://github.com.evil.test/tequed232/Dolphin-Schedule/releases/tag/v1.6.0","http://github.com/tequed232/Dolphin-Schedule/releases/tag/v1.6.0","$RELEASES_URL/tag/v1.6.0?download=1","$RELEASES_URL/tag/v1.5.5").forEach { page ->
            assertThrows(IllegalArgumentException::class.java) { parseOfficialRelease(raw().put("html_url",page).toString()) }
        }
    }
    @Test fun foreignOrMismatchedAssetsCannotBecomeDownloadTargetsAndNotesAreBounded() {
        val candidates=listOf("https://example.test/app.apk","$RELEASES_URL/download/v1.5.5/app.apk","$RELEASES_URL/download/v1.6.0/../app.apk","$RELEASES_URL/download/v1.6.0/%2e%2e%2fapp.apk","$RELEASES_URL/download/v1.6.0/app.apk?token=bad","https://user@github.com/tequed232/Dolphin-Schedule/releases/download/v1.6.0/app.apk")
        candidates.forEach { url -> assertNull(url,parseOfficialRelease(raw(assets=listOf("app.apk" to url)).toString()).assetUrl) }
        assertEquals(12_000,parseOfficialRelease(raw().put("body","学".repeat(20_000)).toString()).notes.length)
        assertThrows(IllegalArgumentException::class.java) { parseOfficialRelease(" ".repeat(2_000_001)) }
    }
}
