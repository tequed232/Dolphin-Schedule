package com.dolphin.schedule.ui.books

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dolphin.schedule.ui.navigation.rememberPredictivePageBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dolphin.schedule.domain.*
import com.dolphin.schedule.media.LocalImages
import com.dolphin.schedule.ui.DolphinViewModel
import com.dolphin.schedule.ui.NativePage
import com.dolphin.schedule.ui.common.SystemBarAppearance
import com.dolphin.schedule.ui.common.dolphinInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 系统相机仅能访问这个 FileProvider URI，不额外申请相机或存储权限。 */
internal class BookCameraContract: ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context,input: Uri): Intent=super.createIntent(context,input).apply {
        clipData=ClipData.newRawUri("教材封面",input)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}

@Composable
internal fun BookCover(url: String?,description: String,modifier: Modifier=Modifier) {
    val image by produceState<ImageBitmap?>(null,url) {
        value=null
        if(url!=null)value=withContext(Dispatchers.Default) { LocalImages.bitmap(url)?.asImageBitmap() }
    }
    Surface(modifier.clip(RoundedCornerShape(12.dp)),color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(12.dp)) {
        val current=image
        if(current!=null)Image(current,description,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Box(contentAlignment=Alignment.Center) { Text("书",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun BookCard(book: Book,modifier: Modifier=Modifier,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
            BookCover(book.cover,"${book.title}封面",Modifier.width(58.dp).height(78.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(book.title.ifBlank { "未填写书名" },style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(book.publisher.ifBlank { "出版社未填写" },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(book.edition.ifBlank { "版次未填写" },style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun NativeBooksScreen(data: AppData,model: DolphinViewModel) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.testTag("book-library")) {
        item { Text("教材与封面保存在本机，同名课程共用教材。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(data.books.entries.sortedBy { it.value.title },key={it.key}) { (key,book) ->
            val owners=data.schedule.courses.filter { it.name==key || it.id==key }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                BookCard(book,Modifier.fillMaxWidth().testTag("book-entry-$key"),onClick={model.books.start(data,key=key)})
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(owners.firstOrNull()?.name ?: "未关联当前课程",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                    owners.firstOrNull()?.let { owner -> TextButton(onClick={model.show(owner)}) { Text("查看课程") } }
                }
            }
        }
        if(data.books.isEmpty())item { Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("还没有标记教材",style=MaterialTheme.typography.titleMedium)
            Text("从课程详情填写书名，或拍照、选图添加本地封面。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={model.open(NativePage.MANAGE)}) { Text("选择一门课程") }
        } } }
    }
}

@Composable
fun NativeBookEditor(controller: BookController,settings: Preferences=Preferences()) {
    val insets=dolphinInsets(settings)
    val d=controller.draft
    val album=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),controller::albumResult)
    val camera=rememberLauncherForActivityResult(BookCameraContract(),controller::cameraResult)
    var reloadConfirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest=controller::requestClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,dismissOnBackPress=false)) {
        SystemBarAppearance()
        val predictiveBack=rememberPredictivePageBack(enabled=true,route=controller) { controller.requestClose() }
        Scaffold(modifier=Modifier.fillMaxSize().imePadding().then(predictiveBack),containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=insets,topBar={
            Row(Modifier.fillMaxWidth().windowInsetsPadding(insets.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=controller::requestClose,enabled=!controller.busy) { Text("取消") }
                Text(if(d?.original!=null)"编辑教材" else "标记教材",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Button(onClick={controller.save()},enabled=!controller.busy && d!=null,modifier=Modifier.testTag("book-save")) { Text("保存") }
            }
        }) { padding ->
            if(d==null)Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).testTag("book-editor"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    BookCover(d.value.cover,"待保存教材封面",Modifier.width(82.dp).height(112.dp).testTag("book-cover-preview"))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(d.ownerName ?: "教材信息",style=MaterialTheme.typography.titleSmall)
                        Text("封面只保存在本机\n请核对教材名称与版次",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(d.value.title,{controller.patch("title",it)},Modifier.fillMaxWidth().testTag("book-title"),label={Text("书名")},enabled=!controller.busy,singleLine=true,shape=RoundedCornerShape(16.dp))
                OutlinedTextField(d.value.publisher,{controller.patch("publisher",it)},Modifier.fillMaxWidth().testTag("book-publisher"),label={Text("出版社")},enabled=!controller.busy,singleLine=true,shape=RoundedCornerShape(16.dp))
                OutlinedTextField(d.value.edition,{controller.patch("edition",it)},Modifier.fillMaxWidth().testTag("book-edition"),label={Text("版次")},enabled=!controller.busy,singleLine=true,shape=RoundedCornerShape(16.dp))
                OutlinedTextField(d.value.text,{controller.patch("text",it)},Modifier.fillMaxWidth().testTag("book-text"),label={Text("封面文字（可选）")},enabled=!controller.busy,minLines=2,shape=RoundedCornerShape(16.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={ if(controller.prepareAlbum())runCatching { album.launch(arrayOf("image/*")) }.onFailure { controller.mediaLaunchFailed(false) } },enabled=!controller.busy) { Text("从相册选图") }
                    OutlinedButton(onClick={ controller.prepareCamera()?.let { uri -> runCatching { camera.launch(uri) }.onFailure { controller.mediaLaunchFailed(true) } } },enabled=!controller.busy) { Text("拍摄封面") }
                    if(d.value.cover!=null)TextButton(onClick=controller::removeCover,enabled=!controller.busy) { Text("移除封面") }
                }
                if(controller.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                controller.error?.let { error ->
                    Text(error,color=MaterialTheme.colorScheme.error)
                    TextButton(onClick={reloadConfirm=true},enabled=!controller.busy) { Text("重新读取已保存内容") }
                }
                if(d.original!=null)TextButton(onClick=controller::requestRemove,enabled=!controller.busy) { Text("移除教材",color=MaterialTheme.colorScheme.error) }
            }
        }
        if(controller.discard)AlertDialog(onDismissRequest=controller::continueEditing,title={Text("放弃教材修改？")},text={Text("尚未保存的文字和封面会被丢弃。")},confirmButton={TextButton(onClick=controller::discardChanges) { Text("放弃修改") }},dismissButton={TextButton(onClick=controller::continueEditing) { Text("继续编辑") }})
        if(controller.removing)AlertDialog(onDismissRequest=controller::cancelRemove,title={Text("移除这份教材？")},text={Text("将移除教材信息和本地封面，课程与相册原图保留。")},confirmButton={TextButton(onClick={controller.save(true)},enabled=!controller.busy) { Text("确认移除") }},dismissButton={TextButton(onClick=controller::cancelRemove,enabled=!controller.busy) { Text("取消") }})
        if(reloadConfirm)AlertDialog(onDismissRequest={reloadConfirm=false},title={Text("重新读取教材？")},text={Text("当前未保存的修改会被放弃。")},confirmButton={TextButton(onClick={reloadConfirm=false;controller.reload()}) { Text("重新读取") }},dismissButton={TextButton(onClick={reloadConfirm=false}) { Text("保留草稿") }})
    }
}
