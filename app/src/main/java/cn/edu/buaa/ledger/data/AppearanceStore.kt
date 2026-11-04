package cn.edu.buaa.ledger.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences

private val Context.appearance by preferencesDataStore("appearance")
class AppearanceStore(private val context: Context) {
    private val key = stringPreferencesKey("theme")
    val theme = context.appearance.data.catch { emit(emptyPreferences()) }.map { it[key] ?: "system" }
    suspend fun setTheme(value: String) { require(value in listOf("system", "light", "dark")); context.appearance.edit { it[key] = value } }
}
