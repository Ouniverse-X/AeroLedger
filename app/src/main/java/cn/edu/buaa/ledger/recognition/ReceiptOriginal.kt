package cn.edu.buaa.ledger.recognition

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException

/** Bitmap stays outside SavedState; a bounded private source survives activity recreation. */
@Composable
internal fun ReceiptOriginal(name: String, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(name) { mutableStateOf<Bitmap?>(null) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    var scale by remember(name) { mutableFloatStateOf(1f) }
    var offset by remember(name) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(name) {
        try { bitmap = ReceiptImageReader(context).preview(name) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "原图无法打开，请重新导入。" }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text("关闭原图") }
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("重置缩放") }
                }
                Text("原图 · 双指缩放和拖动，核对金额、日期及交易状态", Modifier.padding(12.dp))
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().pointerInput(name) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 64f)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        offset = (offset - (centroid - center)) * (next / scale) + (centroid - center) + pan
                        scale = next
                    }
                }) {
                    if (error != null) Text(error!!, Modifier.padding(20.dp))
                    else if (bitmap == null) CircularProgressIndicator(Modifier.padding(20.dp))
                    else Image(bitmap!!.asImageBitmap(), contentDescription = "本次导入的账单原图",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        })
                }
            }
        }
    }
}
