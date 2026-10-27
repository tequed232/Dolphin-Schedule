package com.dolphin.schedule.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dolphin.schedule.R
import com.dolphin.schedule.ui.NativePage
import com.dolphin.schedule.ui.common.DolphinSymbol

/** 图标只作装饰，文字标签承担辅助功能名称。 */
@Composable
internal fun SettingsIconBadge(page: NativePage) {
    val resource=when(page) {
        NativePage.IMPORT,NativePage.FILE_IMPORT->R.drawable.ms_upload_file
        NativePage.TERM->R.drawable.ms_calendar_month
        NativePage.TIMES->R.drawable.ms_schedule
        NativePage.NAVIGATION->R.drawable.ms_near_me
        NativePage.MANAGE->R.drawable.ms_event_note
        NativePage.BOOKS->R.drawable.ms_menu_book
        NativePage.HOME->R.drawable.ms_home
        NativePage.APPEARANCE->R.drawable.ms_palette
        NativePage.NOTIFICATIONS->R.drawable.ms_notifications_active
        NativePage.UPDATES->R.drawable.ms_system_update_alt
        NativePage.DATA->R.drawable.ms_database
        NativePage.ABOUT->R.drawable.ms_info
        NativePage.BACKGROUND->R.drawable.ms_wallpaper
        NativePage.SAFE_AREA->R.drawable.ms_fullscreen
    }
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.secondaryContainer,
        contentColor=MaterialTheme.colorScheme.onSecondaryContainer) {
        Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) { DolphinSymbol(resource,Modifier.size(22.dp)) }
    }
}
