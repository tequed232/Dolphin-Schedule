package com.dolphin.schedule.notifications

import org.junit.Assert.*
import org.junit.Test

class CourseNotificationPresentationTest {
    @Test fun journeyKeepsFullDestinationOnlyInMapAction() {
        val room="16栋203号教室"
        val view=courseNotificationPresentation("智慧财经素养",room,"智慧财经素养教程")
        assertEquals("智慧财经素养",view.title)
        assertEquals("去$room",view.destinationAction)
        assertEquals("16栋",view.collapsedBuilding)
        assertFalse(view.title.contains(room));assertFalse(view.text.contains(room))
        assertFalse(view.text.contains("2/3"));assertFalse(view.text.contains("①"))
        assertEquals("正在前往教室\n教材：智慧财经素养教程",view.text)
    }
    @Test fun longCourseNameRemainsWholeAndSeparateFromAddress() {
        val name="习近平新时代中国特色社会主义思想概论与实践课程"
        val view=courseNotificationPresentation(name,"教学楼A203号教室")
        assertEquals(name,view.title);assertEquals("去教学楼A203号教室",view.destinationAction)
        assertEquals("教学楼",view.collapsedBuilding);assertFalse(view.title.contains("A203"))
    }
    @Test fun emptyRoomDoesNotProduceFakeAddress() {
        for(room in listOf("","  ","教室待填写")) {
            val view=courseNotificationPresentation("课程",room)
            assertEquals("去教室",view.destinationAction);assertEquals("去上课",view.collapsedBuilding)
            assertEquals("正在前往教室",view.text)
        }
    }
    @Test fun buildingCapsuleRetainsShortLabelsAndFallbackForLongOrUnknownPlace() {
        for((room,building) in listOf("16栋203号教室" to "16栋","三幢201" to "三幢","图书馆101" to "图书馆","非常长的校区教学楼101" to "去上课","操场西区" to "去上课")) {
            val view=courseNotificationPresentation("课程",room)
            assertEquals(building,view.collapsedBuilding);assertTrue(view.collapsedBuilding.length<=6)
            assertEquals("去$room",view.destinationAction)
        }
    }
    @Test fun unavailableBookDoesNotAddPlaceholderNoise() {
        for(book in listOf("","  ","尚未标记"))assertEquals("正在前往教室",courseNotificationPresentation("课程","16栋203号教室",book).text)
    }
    @Test fun academicRoomCodeProducesReadableBuildingAndDestinationWithoutGuessingOtherPlaces() {
        for((raw,building,action) in listOf(
            Triple("1-401 (QY1-401)","1栋","去1栋401号教室"),
            Triple("16-203（QY16-203）","16栋","去16栋203号教室"),
            Triple("3-402 (QY54)","3栋","去3栋402号教室"),
            Triple("27-305","27栋","去27栋305号教室"),
            Triple("操场西区","去上课","去操场西区"),
        )) {
            val view=courseNotificationPresentation("课程",raw)
            assertEquals(building,view.collapsedBuilding);assertEquals(action,view.destinationAction)
        }
    }
}
