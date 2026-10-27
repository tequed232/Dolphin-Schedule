package com.dolphin.schedule.domain

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class MapLaunchPlanTest {
    private fun target(map: String="amap")=courseDestination(
        Course(name="不传课名",teacher="不传教师",room="16-203 (QY16-203)",day=1,start=1,end=2,weeks=listOf(1)),
        Preferences(school="合成学校 & 北校区",map=map),
    )
    @Test fun nativeAmapUsesOfficialKeywordEntryWithOnlyTheExistingSchoolAndBuilding() {
        val target=target();val plan=mapLaunchPlan(target.url);val native=URI(requireNotNull(plan.nativeUrl))
        assertEquals("androidamap",native.scheme);assertEquals("poi",native.host)
        assertEquals("com.autonavi.minimap",plan.nativePackage);assertEquals(target.url,plan.webUrl)
        val query=native.rawQuery.split('&').associate { val pair=it.split('=',limit=2);pair[0] to URLDecoder.decode(pair[1],"UTF-8") }
        assertEquals("Dolphin Schedule",query["sourceApplication"])
        assertEquals("合成学校 & 北校区 16栋",query["keywords"]);assertEquals("0",query["dev"])
        assertEquals(setOf("sourceApplication","keywords","dev"),query.keys)
        assertFalse(plan.nativeUrl!!.contains("203"))
    }
    @Test fun baiduKeepsItsExistingWebEntry() {
        val target=target("baidu");val plan=mapLaunchPlan(target.url)
        assertEquals(target.url,plan.webUrl);assertNull(plan.nativeUrl);assertNull(plan.nativePackage)
    }
    @Test fun arbitraryOrAmbiguousLinksCannotBecomeNativeLaunchRequests() {
        listOf(
            "http://uri.amap.com/search?keyword=school",
            "https://uri.amap.com.evil.test/search?keyword=school",
            "https://evil@uri.amap.com/search?keyword=school",
            "https://uri.amap.com:444/search?keyword=school",
            "https://uri.amap.com/search?keyword=a&keyword=b",
            "https://uri.amap.com/search?keyword=",
            "https://uri.amap.com/search?keyword=a%0Ab",
            "intent://school#Intent;end",
        ).forEach { url->assertThrows(url,Exception::class.java) { mapLaunchPlan(url) } }
    }
}
