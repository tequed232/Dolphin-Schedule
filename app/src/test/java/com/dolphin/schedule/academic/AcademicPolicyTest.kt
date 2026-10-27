package com.dolphin.schedule.academic

import org.junit.Assert.*
import org.junit.Test

class AcademicPolicyTest {
    @Test fun exactOriginAndScheduleOnly() {
        assertTrue(AcademicPolicy.allowed(AcademicPolicy.ENTRY))
        assertTrue(AcademicPolicy.schedule(AcademicPolicy.ENTRY))
        assertFalse(AcademicPolicy.schedule("${AcademicPolicy.ORIGIN}/#/login"))
        assertTrue(AcademicPolicy.allowed("https://sso.gdcmxy.edu.cn/authserver/login?service=example"))
        assertTrue(AcademicPolicy.allowed("https://sso.gdcmxy.edu.cn:443/authserver/login"))
        assertFalse(AcademicPolicy.allowed("http://sso.gdcmxy.edu.cn/authserver/login"))
        assertFalse(AcademicPolicy.allowed("https://sso.gdcmxy.edu.cn.evil.test/authserver/login"))
        assertFalse(AcademicPolicy.allowed("https://sso.gdcmxy.edu.cn:1004/authserver/login"))
        assertFalse(AcademicPolicy.schedule("https://sso.gdcmxy.edu.cn/#/jwxt/js/student/personal/studentSchedule"))
        listOf("http://jwxt.gdcmxy.edu.cn:1004/", "https://jwxt.gdcmxy.edu.cn/", "https://jwxt.gdcmxy.edu.cn.evil.test:1004/", "https://evil.test@jwxt.gdcmxy.edu.cn:1004/", "file:///etc/passwd", "javascript:alert(1)").forEach { assertFalse(it,AcademicPolicy.allowed(it)) }
    }
    @Test fun resultNamesCannotEscapePrivateCache() {
        assertTrue(AcademicPolicy.requestId("550e8400-e29b-41d4-a716-446655440000"))
        listOf("../../secret", "", "a/../../", "550e8400-e29b-41d4-a716-446655440000/../x").forEach { assertFalse(AcademicPolicy.requestId(it)) }
    }
    @Test fun editedAddressOnlyNavigatesToSupportedHttpsHosts() {
        assertEquals(AcademicPolicy.ENTRY,AcademicPolicy.destination("  ${AcademicPolicy.ENTRY}  "))
        assertEquals("https://jwxt.gdcmxy.edu.cn:1004/",AcademicPolicy.destination("jwxt.gdcmxy.edu.cn:1004/"))
        assertNotNull(AcademicPolicy.destination("sso.gdcmxy.edu.cn/authserver/login"))
        listOf("", "javascript:alert(1)", "http://jwxt.gdcmxy.edu.cn:1004/", "https://jwxt.gdcmxy.edu.cn:1004.evil.test/", "https://evil.test/").forEach { assertNull(AcademicPolicy.destination(it)) }
    }
    @Test fun inUseProfileDefersDataCleanupWithoutBlockingFreshSession() {
        val deleted = mutableListOf<String>(); val deferred = mutableListOf<String>()
        val busy = "${AcademicPolicy.PROFILE_PREFIX}busy"; val stale = "${AcademicPolicy.PROFILE_PREFIX}stale"; val keep = "${AcademicPolicy.PROFILE_PREFIX}keep"
        AcademicProfileCleanup.clean(listOf("Default",busy,stale,keep),keep,emptySet(),{ if(it == busy) throw IllegalStateException("in-use"); deleted.add(it); true },{deferred.add(it)})
        assertEquals(listOf(stale),deleted); assertEquals(listOf(busy),deferred)
        AcademicProfileCleanup.clean(listOf(busy),null,setOf(busy),{ fail("Retired profile must wait for a fresh process"); false },{ fail("Data already cleared") })
    }
}
