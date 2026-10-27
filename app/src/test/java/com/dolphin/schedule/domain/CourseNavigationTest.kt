package com.dolphin.schedule.domain

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.net.URI

class CourseNavigationTest {
    private val course=Course(id="nav",name="不可发送的课名",teacher="不可发送的教师",room="26-207 (QY26-207)",day=1,start=1,end=2,weeks=listOf(1))
    @Test fun onlySchoolAndBuildingReachMapAndReservedCharactersAreEncoded() {
        val result=courseDestination(course,Preferences(school="合成学校 & 北校区"))
        assertEquals("26栋",result.building)
        val uri=URI(result.url)
        assertEquals("uri.amap.com",uri.host)
        val query=uri.rawQuery.split('&').associate { val parts=it.split('=',limit=2);parts[0] to URLDecoder.decode(parts[1],"UTF-8") }
        assertEquals("合成学校 & 北校区 26栋",query["keyword"])
        assertEquals("1",query["callnative"])
        assertFalse(result.url.contains("207"))
        assertFalse(result.url.contains(course.name))
        assertFalse(result.url.contains(course.teacher))
        assertEquals(course.room,result.room)
    }
    @Test fun namedBuildingsAndBaiduUseSameDestination() {
        assertEquals("图书馆",navigationBuilding("地点：图书馆 A305室"))
        assertEquals("27栋",navigationBuilding("合成大学27栋305号教室"))
        assertEquals("3栋",navigationBuilding("003#402"))
        val result=courseDestination(course.copy(room="实验楼305室"),Preferences(school="合成学院",map="baidu"))
        assertEquals("实验楼",result.building)
        assertEquals("api.map.baidu.com",URI(result.url).host)
        assertTrue(URLDecoder.decode(URI(result.url).rawQuery,"UTF-8").contains("address=合成学院 实验楼&"))
    }
    @Test fun missingSchoolOrUncertainRoomCannotLaunchNavigation() {
        assertThrows(IllegalArgumentException::class.java) { courseDestination(course,Preferences(school="")) }
        listOf("","待定","…","305号教室").forEach { room ->
            assertThrows(IllegalArgumentException::class.java) { courseDestination(course.copy(room=room),Preferences(school="合成学校")) }
        }
        assertEquals("26栋",navigationBuilding("26栋…"))
    }
}
