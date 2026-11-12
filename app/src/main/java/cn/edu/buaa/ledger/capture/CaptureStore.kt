package cn.edu.buaa.ledger.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** One private, short-lived result. Neither the service nor this store can save a ledger entry. */
class CaptureStore(private val context: Context) {
    private val folder get() = File(context.cacheDir, "captures").apply { mkdirs() }
    private val prefs get() = context.getSharedPreferences("capture-result", Context.MODE_PRIVATE)
    fun save(bitmap: Bitmap) {
        clear()
        val file = File(folder, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            prefs.edit().putString("file", file.name).putLong("time", System.currentTimeMillis()).remove("error").apply()
        } catch (failure: Exception) { file.delete(); throw failure }
    }
    fun result(): android.net.Uri? {
        val name = prefs.getString("file", null) ?: return null
        if (System.currentTimeMillis() - prefs.getLong("time", 0) > 60 * 60 * 1000) { clear(); return null }
        if (!Regex("[a-f0-9-]+\\.png").matches(name)) return null
        val file = File(folder, name)
        return if (file.isFile) FileProvider.getUriForFile(context, "${context.packageName}.capture", file) else null
    }
    fun failure(message: String) { clear(); prefs.edit().putString("error", message).apply() }
    fun message(): String? = prefs.getString("error", null)
    fun clear() {
        folder.listFiles()?.filter { it.isFile && it.extension == "png" }?.forEach { it.delete() }
        prefs.edit().clear().apply()
    }
}
