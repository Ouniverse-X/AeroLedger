package cn.edu.buaa.ledger.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject

/** Small form drafts survive app restarts; transfer files use atomic replacement. */
class DraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("editor_drafts",Context.MODE_PRIVATE)
    private val pending = AtomicFile(java.io.File(context.filesDir,"pending-import.json"))
    private val restore = AtomicFile(java.io.File(context.filesDir,"pending-restore.json"))
    fun read(key: String): Map<String,String> = runCatching {
        val objectValue = JSONObject(prefs.getString(key,null) ?: return emptyMap())
        objectValue.keys().asSequence().associateWith { objectValue.getString(it) }
    }.getOrDefault(emptyMap())
    fun save(key: String, values: Map<String,String>) {
        val text = JSONObject(values).toString()
        if (prefs.getString(key,null) != text) prefs.edit().putString(key,text).apply()
    }
    fun clear(key: String) { prefs.edit().remove(key).apply() }
    fun clearEditors() { prefs.edit().clear().apply() }
    @Synchronized fun saveImport(text: String, filename: String) {
        val stream = pending.startWrite()
        try { stream.write(JSONObject().put("text",text).put("filename",filename).toString().toByteArray(Charsets.UTF_8)); pending.finishWrite(stream) }
        catch (error: Exception) { pending.failWrite(stream); throw error }
    }
    @Synchronized fun readImport(): Pair<String,String>? = runCatching {
        JSONObject(pending.readFully().toString(Charsets.UTF_8)).let { it.getString("text") to it.getString("filename") }
    }.getOrNull()
    @Synchronized fun clearImport() { pending.delete() }
    @Synchronized fun saveRestore(text: String, filename: String) {
        val stream=restore.startWrite()
        try { stream.write(JSONObject().put("text",text).put("filename",filename).toString().toByteArray(Charsets.UTF_8));restore.finishWrite(stream) }
        catch(error:Exception){restore.failWrite(stream);throw error}
    }
    @Synchronized fun readRestore(): Pair<String,String>? = runCatching {
        JSONObject(restore.readFully().toString(Charsets.UTF_8)).let { it.getString("text") to it.getString("filename") }
    }.getOrNull()
    @Synchronized fun clearRestore() { restore.delete() }
}
