package com.dolphin.schedule.domain

import java.net.URLEncoder

data class CourseDestination(val room: String,val building: String,val url: String)

/** 仅把学校和楼栋交给地图；不把课名、教师或完整课表放入链接。 */
fun navigationBuilding(value: String): String {
    var room=value.trim().replace(Regex("^(?:教室|地点)[:：]\\s*"),"").replace(Regex("\\s+")," ")
    room=room.replace(Regex("^.{2,25}?(?:大学|学院|学校)(?:.{0,12}?校区)?(?=.{1,20}(?:楼|栋|教室))"),"").trim()
    Regex("^(\\d{1,3})\\s*(?:栋|楼|[-－—#])").find(room)?.let { return "${it.groupValues[1].toInt()}栋" }
    Regex("^(.{1,24}?(?:栋|楼|馆|体育场))").find(room)?.let { return it.groupValues[1] }
    return if(Regex("(?:\\.{2,}|…|待定)").containsMatchIn(room) || Regex("[A-Za-z]?\\d{2,4}(?:号)?(?:教室|室)?").matches(room))"" else room
}

fun courseDestination(course: Course,preferences: Preferences): CourseDestination {
    require(preferences.school.isNotBlank()) { "请先填写学校名称，才能准确导航到楼栋" }
    require(course.room.isNotBlank()) { "这门课尚未填写教室，请先编辑课程" }
    val building=navigationBuilding(course.room)
    require(building.isNotBlank()) { "教室数据不完整，无法确定楼栋；请先编辑课程" }
    val query=URLEncoder.encode("${preferences.school.trim()} $building","UTF-8").replace("+","%20")
    val url=if(preferences.map=="baidu")"https://api.map.baidu.com/geocoder?address=$query&output=html&src=dolphin.schedule"
        else "https://uri.amap.com/search?keyword=$query&callnative=1"
    return CourseDestination(course.room,building,url)
}
