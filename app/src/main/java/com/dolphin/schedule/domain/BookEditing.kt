package com.dolphin.schedule.domain

import java.util.UUID

/** 旧版以课名共享教材；仅兼容已经存在的原生 ID 键，不重写或丢弃历史键。 */
fun bookKey(data: AppData,course: Course): String = when {
    data.books.containsKey(course.name)->course.name
    data.books.containsKey(course.id)->course.id
    else->course.name
}
fun courseBook(data: AppData,course: Course): Book? = data.books[bookKey(data,course)]

/** 通知与系统日历继续以课名读教材；保留未关联的历史条目。 */
fun notificationBooks(data: AppData): Map<String,Book> = data.books.toMutableMap().apply {
    data.schedule.courses.forEach { course -> courseBook(data,course)?.let { put(course.name,it) } }
}

data class BookDraft(
    val id: String=UUID.randomUUID().toString(),val key: String,val original: Book?,val seed: Book,val value: Book,
    val ownerId: String?=null,val ownerName: String?=null,val returnToCourse: Boolean=false,
)

fun bookDraft(data: AppData,course: Course?=null,key: String?=null,returnToCourse: Boolean=false): BookDraft {
    require(course!=null || key!=null)
    val target=course?.let { bookKey(data,it) } ?: key!!
    require(target.isNotBlank() && target.length<=500)
    val stored=data.books[target]
    require(course!=null || stored!=null) { "教材已移除，请重新打开教材列表" }
    val seed=stored ?: Book(title=course?.name.orEmpty())
    return BookDraft(key=target,original=stored,seed=seed,value=seed,ownerId=course?.id,ownerName=course?.name,returnToCourse=returnToCourse)
}

fun saveBook(data: AppData,draft: BookDraft,remove: Boolean=false): AppData {
    draft.ownerId?.let { id ->
        val owner=data.schedule.courses.firstOrNull { it.id==id }
        require(owner!=null && owner.name==draft.ownerName && bookKey(data,owner)==draft.key) { "教材所属课程已变化，请保留草稿并重新核对" }
    }
    require(data.books[draft.key]==draft.original) { "教材已更新，请保留草稿，或重新读取已保存的内容" }
    val books=data.books.toMutableMap()
    if(remove)books.remove(draft.key) else {
        val book=draft.value.copy(title=draft.value.title.trim(),publisher=draft.value.publisher.trim(),edition=draft.value.edition.trim())
        require(book.title.length in 1..300) { "请填写 1～300 字的书名" }
        require(book.publisher.length<=500 && book.edition.length<=500 && book.text.length<=10000) { "教材文字过长，请精简后保存" }
        require(book.cover==null || book.cover.length<=3_000_000) { "封面过大，请重新选择图片" }
        books[draft.key]=book
    }
    return data.copy(books=books)
}
