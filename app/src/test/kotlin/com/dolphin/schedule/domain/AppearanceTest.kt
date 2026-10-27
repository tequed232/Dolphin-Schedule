package com.dolphin.schedule.domain

import com.dolphin.schedule.data.AppDataCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    private val image=Background("data:image/png;base64,synthetic","合成背景",900,1600,1800,3200)
    private val data=AppData(background=image,settings=Preferences(backgroundEnabled=true,backgroundBlur=8,topAuto=false,topInset=24,bottomAuto=false,bottomInset=18),books=mapOf("课程" to Book("教材")))
    @Test fun backgroundIdentityIgnoresOtherPreferencesAndCourseEdits() {
        val token=backgroundFingerprint(backgroundState(data))
        assertEquals(token,backgroundFingerprint(backgroundState(data.copy(settings=data.settings.copy(mode="dark",scale=1.3f,backgroundBlur=20),books=emptyMap()))))
        assertEquals(64,token.length)
        assertNotEquals(token,backgroundFingerprint(backgroundState(data.copy(background=image.copy(name="另一张")))))
        assertNotEquals(token,backgroundFingerprint(backgroundState(data.copy(background=image.copy(sourceHeight=6400)))))
        assertNotEquals(token,backgroundFingerprint(backgroundState(data.copy(settings=data.settings.copy(backgroundEnabled=false)))))
    }
    @Test fun asynchronousReplacementKeepsUnrelatedUpdatesAndRejectsNewerBackground() {
        val token=backgroundFingerprint(backgroundState(data));val next=image.copy(name="新背景")
        val edited=data.copy(settings=data.settings.copy(school="新学校",backgroundBlur=12),books=data.books+("另一门课" to Book("另一教材")))
        val saved=replaceBackground(edited,token,next)
        assertEquals(next,saved.background);assertEquals(edited.books,saved.books);assertEquals("新学校",saved.settings.school);assertEquals(12,saved.settings.backgroundBlur)
        assertThrows(IllegalArgumentException::class.java) { replaceBackground(data.copy(background=image.copy(url="data:image/png;base64,newer")),token,next) }
        assertThrows(IllegalArgumentException::class.java) { replaceBackground(data.copy(settings=data.settings.copy(backgroundEnabled=false)),token,next) }
    }
    @Test fun themeOnlyKeepsPhotoAndRemovalOnlyTouchesBackground() {
        val disabled=changeBackground(data,backgroundState(data),false)
        assertEquals(image,disabled.background);assertFalse(disabled.settings.backgroundEnabled)
        assertTrue(changeBackground(disabled,backgroundState(disabled),true).settings.backgroundEnabled)
        val removed=changeBackground(data,backgroundState(data),false,true)
        assertNull(removed.background);assertEquals(data.schedule,removed.schedule);assertEquals(data.books,removed.books)
        assertThrows(IllegalArgumentException::class.java) { changeBackground(removed,backgroundState(removed),true) }
    }
    @Test fun blurGuardRejectsConcurrentAdjustmentsAndInactivePhoto() {
        val saved=changeBackgroundBlur(data,backgroundState(data),8,24)
        assertEquals(24,saved.settings.backgroundBlur);assertEquals(data.background,saved.background)
        assertThrows(IllegalArgumentException::class.java) { changeBackgroundBlur(saved,backgroundState(data),8,12) }
        assertThrows(IllegalArgumentException::class.java) { changeBackgroundBlur(data,backgroundState(data),8,31) }
        val disabled=data.copy(settings=data.settings.copy(backgroundEnabled=false))
        assertThrows(IllegalArgumentException::class.java) { changeBackgroundBlur(disabled,backgroundState(disabled),8,12) }
    }
    @Test fun backupRetainsManualInsetsPhotoAndAppearance() {
        assertEquals(data,AppDataCodec.decode(AppDataCodec.encode(data)))
        val malformed=JSONObject().put("topInset",-40).put("bottomInset",999).put("backgroundBlur",900).put("mode","unknown").put("performance","unknown")
        val preferences=AppDataCodec.decodePreferences(malformed)
        assertEquals(0,preferences.topInset);assertEquals(100,preferences.bottomInset);assertEquals(30,preferences.backgroundBlur)
        assertEquals("system",preferences.mode);assertEquals("auto",preferences.performance)
    }
    @Test fun frameBudgetAdaptsToActualRefreshRateAndWaitsForACompleteSample() {
        fun frames(ms: Double,count: Int=24)=List(count) { (ms*1_000_000).toLong() }
        assertFalse(shouldReduceBackgroundFrames(frames(16.7),60f))
        assertFalse(shouldReduceBackgroundFrames(frames(8.4),120f))
        assertFalse(shouldReduceBackgroundFrames(frames(33.4),30f))
        assertTrue(shouldReduceBackgroundFrames(frames(16.7),120f))
        assertFalse(shouldReduceBackgroundFrames(frames(50.0,23),60f))
        assertTrue(shouldReduceBackgroundFrames(frames(16.7,20)+frames(50.0,4),60f))
        assertFalse(shouldReduceBackgroundFrames(frames(16.7),Float.NaN))
    }
}
