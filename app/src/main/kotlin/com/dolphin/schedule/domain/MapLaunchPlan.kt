package com.dolphin.schedule.domain

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class MapLaunchPlan(val webUrl: String,val nativeUrl: String?=null,val nativePackage: String?=null)

/** 使用已有导航目标构造官方客户端入口；不增加定位、SDK 或坐标猜测。 */
fun mapLaunchPlan(webUrl: String): MapLaunchPlan {
    val uri=URI(webUrl)
    require(uri.scheme=="https" && uri.rawUserInfo==null && uri.port in listOf(-1,443) && uri.fragment==null) { "地图链接不可用" }
    if(uri.host=="api.map.baidu.com" && uri.path=="/geocoder")return MapLaunchPlan(webUrl)
    require(uri.host=="uri.amap.com" && uri.path=="/search") { "不支持的地图链接" }
    val keywords=uri.rawQuery.orEmpty().split('&').map { it.split('=',limit=2) }
        .filter { it[0]=="keyword" }
    require(keywords.size==1 && keywords.single().size==2) { "地图搜索目标缺失" }
    val keyword=URLDecoder.decode(keywords.single()[1],"UTF-8")
    require(keyword.isNotBlank() && keyword.length<=500 && keyword.none { it.isISOControl() }) { "地图搜索目标不可用" }
    val encoded=URLEncoder.encode(keyword,"UTF-8").replace("+","%20")
    return MapLaunchPlan(webUrl,"androidamap://poi?sourceApplication=Dolphin%20Schedule&keywords=$encoded&dev=0","com.autonavi.minimap")
}
