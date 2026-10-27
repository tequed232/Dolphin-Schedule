package com.dolphin.schedule

data class CourseNotificationPresentation(
    val title: String,
    val text: String,
    val destinationAction: String,
    val collapsedBuilding: String
)

/** Display fields only: notification identity, navigation and expiry stay with Notifications. */
fun courseNotificationPresentation(
    courseName: String,
    room: String,
    bookTitle: String = ""
): CourseNotificationPresentation {
    val place = room.trim().ifBlank { "教室待填写" }
    val book = bookTitle.trim().takeIf { it.isNotEmpty() && it != "尚未标记" }
    return CourseNotificationPresentation(
        title = courseName.trim().ifBlank { "课程" },
        text = "正在前往教室" + (book?.let { "\n教材：$it" } ?: ""),
        destinationAction = if(place == "教室待填写") "去教室" else "去$place",
        // Retain the existing short building label used by Android 16 / ColorOS capsules.
        collapsedBuilding = Regex("^.*?(?:栋|幢|楼|馆)").find(place)?.value
            ?.takeIf { it.length <= 6 } ?: "去上课"
    )
}
