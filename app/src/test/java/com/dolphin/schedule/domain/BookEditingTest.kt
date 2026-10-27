package com.dolphin.schedule.domain

import com.dolphin.schedule.data.AppDataCodec
import org.junit.Assert.*
import org.junit.Test

class BookEditingTest {
    private val course=Course(id="course-1",name="高等数学",day=1,start=1,end=2,weeks=listOf(1,2))
    private val book=Book("高等数学（上册）","高等教育出版社","第 7 版","data:image/jpeg;base64,cover","封面备注","album")
    private fun data()=AppData(schedule=defaultSchedule().copy(courses=listOf(course)),books=mapOf(course.name to book),onboarded=true)
    @Test fun legacyNameWinsAndIdKeyRemainsReadable() {
        assertEquals(book,courseBook(data(),course))
        val fallback=data().copy(books=mapOf(course.id to book));assertEquals(course.id,bookKey(fallback,course))
        assertEquals(book,courseBook(fallback,course))
        assertEquals(book,courseBook(fallback.copy(books=fallback.books+(course.name to book)),course))
    }
    @Test fun reimportedAndSameNameCoursesShareStoredBook() {
        val other=course.copy(id="new-id",day=2)
        val replacement=data().copy(schedule=data().schedule.copy(courses=listOf(other)))
        assertEquals(book,courseBook(replacement,other))
        val draft=bookDraft(data().copy(schedule=data().schedule.copy(courses=listOf(course,other))),other)
        val updated=saveBook(data(),draft.copy(ownerId=course.id,value=book.copy(title="新教材")))
        assertEquals("新教材",courseBook(updated,course)?.title)
        assertEquals("新教材",courseBook(updated,other)?.title)
    }
    @Test fun newBookUsesNameWithoutInventingPublisher() {
        val empty=data().copy(books=emptyMap());val draft=bookDraft(empty,course)
        assertEquals(course.name,draft.key);assertEquals(course.name,draft.seed.title);assertEquals("",draft.seed.publisher)
        assertTrue(saveBook(empty,draft).books.containsKey(course.name))
    }
    @Test fun unrelatedChangesArePreservedByTargetedSave() {
        val draft=bookDraft(data(),course).copy(value=book.copy(title="更新教材"))
        val changed=data().copy(books=data().books+("其他课程" to Book("另一教材")),settings=data().settings.copy(mode="dark"))
        val saved=saveBook(changed,draft)
        assertEquals(changed.settings,saved.settings);assertEquals(changed.books["其他课程"],saved.books["其他课程"])
    }
    @Test fun changedBookAndChangedOwnerBlockOverwrite() {
        val draft=bookDraft(data(),course)
        assertThrows(IllegalArgumentException::class.java) { saveBook(data().copy(books=mapOf(course.name to book.copy(edition="新版"))),draft) }
        assertThrows(IllegalArgumentException::class.java) { saveBook(data().copy(schedule=data().schedule.copy(courses=emptyList())),draft) }
        assertThrows(IllegalArgumentException::class.java) { saveBook(data().copy(schedule=data().schedule.copy(courses=listOf(course.copy(name="改名课程")))),draft) }
    }
    @Test fun concurrentCanonicalBookBlocksOldIdDraft() {
        val idData=data().copy(books=mapOf(course.id to book));val draft=bookDraft(idData,course)
        assertThrows(IllegalArgumentException::class.java) { saveBook(idData.copy(books=idData.books+(course.name to Book("同名教材"))),draft) }
    }
    @Test fun orphanEditAndRemovalKeepCoursesAndOtherBooks() {
        val orphan=data().copy(books=data().books+("历史课程" to book))
        val draft=bookDraft(orphan,key="历史课程").copy(value=book.copy(text="历史备注"))
        val edited=saveBook(orphan,draft);assertEquals("历史备注",edited.books["历史课程"]?.text)
        val removed=saveBook(orphan,draft,true)
        assertEquals(data().books,removed.books);assertEquals(orphan.schedule,removed.schedule)
    }
    @Test fun titleIsRequiredAndMetadataIsTrimmed() {
        val d=bookDraft(data(),course)
        assertThrows(IllegalArgumentException::class.java) { saveBook(data(),d.copy(value=book.copy(title="  "))) }
        assertThrows(IllegalArgumentException::class.java) { saveBook(data(),d.copy(value=book.copy(text="字".repeat(10001)))) }
        val saved=saveBook(data(),d.copy(value=book.copy(title="  书名  ",publisher=" 社 ",edition=" 版 "))).books[course.name]!!
        assertEquals("书名",saved.title);assertEquals("社",saved.publisher);assertEquals("版",saved.edition)
    }
    @Test fun backupAndNotificationBindingsPreserveNameAndIdBooks() {
        val source=data().copy(books=mapOf(course.id to book,"历史课程" to Book("旧教材")))
        val restored=AppDataCodec.decode(AppDataCodec.encode(source));assertEquals(source,restored)
        assertEquals(book,notificationBooks(restored)[course.name]);assertEquals("旧教材",notificationBooks(restored)["历史课程"]?.title)
    }
}
