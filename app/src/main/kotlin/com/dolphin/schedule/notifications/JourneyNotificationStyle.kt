package com.dolphin.schedule.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import com.dolphin.schedule.R

/** 使用标准模板提供层级与品牌图，保留系统实时活动资格，卡片外形由系统绘制。 */
internal fun Notification.Builder.journeyCard(context: Context,view: CourseNotificationPresentation): Notification.Builder {
    fun bold(value: String)=SpannableString(value).apply {
        setSpan(StyleSpan(Typeface.BOLD),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return setContentTitle(bold(view.title))
        .setContentText(view.status)
        .setStyle(Notification.BigTextStyle().setBigContentTitle(bold(view.title)).bigText(bold(view.text)))
        .setLargeIcon(Icon.createWithResource(context,R.drawable.live_icon))
        .setColorized(false)
}
