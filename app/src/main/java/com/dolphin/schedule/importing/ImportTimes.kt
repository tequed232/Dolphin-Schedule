package com.dolphin.schedule.importing

import com.dolphin.schedule.domain.*
import java.time.LocalTime
import java.text.Normalizer

internal fun text(value: Any?): String=if(value==null) "" else Normalizer.normalize(value.toString(),Normalizer.Form.NFKC).trim()
internal fun minutes(value: String): Int { require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(value)) { "时间格式应为 HH:mm" };return LocalTime.parse(value).toSecondOfDay()/60 }
internal fun clock(value: Int): String { require(value in 0..1439) { "上课时间不能跨越午夜" };return "%02d:%02d".format(java.util.Locale.ROOT,value/60,value%60) }
internal fun normalizeClock(value: Any?): String {
    val raw=text(value).replace(Regex("^(\\d{1,2}:\\d{2}):00$"),"$1").replace(Regex("^(\\d):"),"0$1:")
    minutes(raw);return raw
}
internal fun restLabel(value: Any?): String=when {
    Regex("(?:午休|午间休息|午餐|lunch(?:\\s*break)?)",RegexOption.IGNORE_CASE).matches(text(value))->"午休"
    Regex("(?:课间|休息|break|recess)",RegexOption.IGNORE_CASE).matches(text(value))->"休息"
    Regex("(?:晚休|晚餐|dinner(?:\\s*break)?)",RegexOption.IGNORE_CASE).matches(text(value))->"晚休"
    else->""
}
internal fun timing(value: Any?): Pair<String,String>? {
    val p=map(value)
    fun pick(vararg keys: String)=keys.mapNotNull { p[it] }.firstOrNull { text(it).isNotBlank() }
    val from=pick("startTime","timeStart","上课时间","开始时间","start");val to=pick("endTime","timeEnd","下课时间","结束时间","end")
    val range=if(value is String)value else pick("timeRange","time","时间段","时间","上课下课时间")
    val combined=range?.let {
        val match=Regex("(\\d{1,2}:\\d{2}(?::00)?)\\s*(?:-|–|—|~|至|到)\\s*(\\d{1,2}:\\d{2}(?::00)?)").matchEntire(text(it)) ?: error("时间段应为 HH:mm–HH:mm")
        normalizeClock(match.groupValues[1]) to normalizeClock(match.groupValues[2])
    }
    if(from==null && to==null && combined==null)return null
    require((from!=null && to!=null) || combined!=null) { "上课时间和下课时间必须同时填写" }
    val start=from?.let(::normalizeClock) ?: combined!!.first;val end=to?.let(::normalizeClock) ?: combined!!.second
    require(combined==null || combined==(start to end)) { "时间段与上课 / 下课时间不一致" }
    require(start<end) { "下课时间必须晚于上课时间，且不能跨越午夜" };return start to end
}
internal fun retainRests(periods: List<Period>): List<Period> = periods.mapIndexed { i,p ->
    val next=periods.getOrNull(i+1);val rest=p.breakAfter
    if(rest!=null && (rest.start<p.end || next==null || rest.end>next.start))p.copy(breakAfter=null) else p
}
internal fun applyRests(periods: List<Period>,rests: List<RestTime>): List<Period> {
    val result=periods.toMutableList();val assigned=mutableMapOf<Int,RestTime>()
    rests.forEach { rest ->
        require(minutes(rest.start)<minutes(rest.end)) { "${rest.label}的起止时间无效" }
        val index=result.indices.firstOrNull { i -> val next=result.getOrNull(i+1);result[i].end.isNotBlank() && next!=null && next.start.isNotBlank() && result[i].end<=rest.start && next.start>=rest.end }
            ?: error("${rest.label} ${rest.start}–${rest.end} 不在两节课之间，请补齐休息前后的课时")
        require(assigned[index]==null || assigned[index]==rest) { "第 ${index+1} 节后的休息时间冲突" }
        result[index]=result[index].copy(breakAfter=rest);assigned[index]=rest
    }
    return result
}
internal fun checkedPeriods(periods: List<Period>): List<Period> {
    validateSchedule(defaultSchedule().copy(periods=periods));return periods
}
internal fun generatePeriods(count: Int,first: String,duration: Int,gap: Int): List<Period> {
    require(count in 1..MAX_PERIODS && duration>0 && gap>=0) { "节数、课时或课间无效" }
    val begin=minutes(first)
    return checkedPeriods((0 until count).map { val start=begin+it*(duration+gap);Period(clock(start),clock(start+duration)) })
}
internal fun retimePeriods(periods: List<Period>,first: String,duration: Int?,gap: Int?): List<Period> {
    checkedPeriods(periods);require(duration==null || duration>0);require(gap==null || gap>=0)
    var next=minutes(first)
    return checkedPeriods(retainRests(periods.mapIndexed { i,p ->
        if(i>0)next+=gap ?: (minutes(p.start)-minutes(periods[i-1].end))
        val start=next;next+=duration ?: (minutes(p.end)-minutes(p.start))
        val delta=start-minutes(p.start)
        p.copy(start=clock(start),end=clock(next),breakAfter=p.breakAfter?.let { it.copy(start=clock(minutes(it.start)+delta),end=clock(minutes(it.end)+delta)) })
    }))
}
internal fun resizePeriods(periods: List<Period>,count: Int): List<Period> {
    require(count in 1..MAX_PERIODS) { "课程节数应为 1～24" }
    if(count<=periods.size)return retainRests(periods.take(count))
    if(periods.isEmpty())return generatePeriods(count,"08:00",45,10)
    val result=periods.toMutableList();val last=periods.last();val remaining=count-periods.size;val available=1439-minutes(last.end)
    val gap=minOf(10,maxOf(0,available/(remaining*4)));val duration=minOf(minutes(last.end)-minutes(last.start),available/remaining-gap)
    require(duration>0) { "当天已没有可用时间，请先提前末节时间或重新提供完整作息" }
    repeat(remaining) { val start=minutes(result.last().end)+gap;result+=Period(clock(start),clock(start+duration)) }
    return checkedPeriods(result)
}

data class ImportedTime(val first: Int,val last: Int,val start: String,val end: String,val row: Int,val single: Boolean=false)
internal fun applyTimes(current: Schedule,courses: List<Course>,times: List<ImportedTime>,notes: MutableList<String>): List<Period> {
    if(times.isEmpty())return resizePeriods(current.periods,maxOf(current.periods.size,courses.maxOfOrNull { it.end } ?: 1))
    val values=mutableMapOf<Pair<Int,Boolean>,Pair<String,Int>>()
    fun assign(index: Int,start: Boolean,time: String,row: Int) {
        require(index in 1..MAX_PERIODS) { "第 $row 行的节次应为 1～24" }
        val key=index to start;val previous=values[key]
        require(previous==null || previous.first==time) { "第 $row 行与第 ${previous!!.second} 行冲突：第 $index 节时间不一致" }
        values[key]=time to row
    }
    times.forEach { assign(it.first,true,it.start,it.row);assign(it.last,false,it.end,it.row) }
    val singles=times.filter { it.single }.map { it.first }.toSet();val last=singles.maxOrNull() ?: 0
    val complete=last>0 && (1..last).all { it in singles }
    val count=if(complete)last else maxOf(current.periods.size,courses.maxOfOrNull { it.end } ?: 1,values.keys.maxOf { it.first })
    require(!complete || courses.none { it.end>count }) { "完整时间表只有 $count 节，但课程安排超出此范围" }
    val result=retainRests((1..count).map { i ->
        val old=current.periods.getOrNull(i-1);val start=values[i to true]?.first ?: old?.start;val end=values[i to false]?.first ?: old?.end
        require(start!=null && end!=null) { "第 $i 节没有完整起止时间，新增节次需逐节填写" }
        (old ?: Period(start,end)).copy(start=start,end=end)
    })
    try { checkedPeriods(result) } catch(failure: IllegalArgumentException) { throw IllegalArgumentException("应用时间后：${failure.message}；未提供的节次沿用当前作息，请逐节核对",failure) }
    notes+=if(complete)"文件完整提供第 1～$count 节时间，每日节数设为 $count 节。" else "仅更新文件提供的时间，其余节次沿用当前作息。"
    if(times.any { it.first!=it.last })notes+="连堂只更新首节上课与末节下课时间，中间节次沿用当前作息，未作推算。"
    return result
}
