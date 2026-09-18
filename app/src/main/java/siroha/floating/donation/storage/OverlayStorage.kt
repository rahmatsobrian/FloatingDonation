package siroha.floating.donation.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import siroha.floating.donation.model.AppLayout
import siroha.floating.donation.model.AppSettings
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.util.Logger

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "siroha_prefs")

class OverlayStorage(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_OVERLAYS = stringPreferencesKey("overlays")
        private val KEY_APP_LAYOUTS = stringPreferencesKey("app_layouts")
        private val KEY_SETTINGS = stringPreferencesKey("settings")

        @Volatile
        private var instance: OverlayStorage? = null

        fun getInstance(context: Context): OverlayStorage {
            return instance ?: synchronized(this) {
                instance ?: OverlayStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    // ---- Overlays ----

    fun getOverlays(): Flow<List<OverlayConfig>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[KEY_OVERLAYS] ?: "[]"
            try {
                val type = object : TypeToken<List<OverlayConfig>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveOverlays(overlays: List<OverlayConfig>) {
        Logger.d("STORAGE saveOverlays: count=${overlays.size}, ids=${overlays.map { it.id }}")
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAYS] = gson.toJson(overlays)
        }
    }

    suspend fun addOverlay(overlay: OverlayConfig) {
        Logger.d("STORAGE addOverlay: id=${overlay.id}, name=${overlay.name}")
        val current = getOverlays().first().toMutableList()
        current.add(overlay)
        saveOverlays(current)
    }

    suspend fun updateOverlay(overlay: OverlayConfig) {
        val current = getOverlays().first().toMutableList()
        val index = current.indexOfFirst { it.id == overlay.id }
        Logger.d("STORAGE updateOverlay: id=${overlay.id}, found=${index >= 0}, isActive=${overlay.isActive}")
        if (index >= 0) {
            current[index] = overlay
            saveOverlays(current)
        }
    }

    suspend fun removeOverlay(id: String) {
        val current = getOverlays().first().toMutableList()
        val beforeCount = current.size
        current.removeAll { it.id == id }
        Logger.d("STORAGE removeOverlay: id=$id, before=$beforeCount, after=${current.size}")
        saveOverlays(current)
    }

    // ---- App Layouts ----

    fun getAppLayouts(): Flow<List<AppLayout>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[KEY_APP_LAYOUTS] ?: "[]"
            try {
                val type = object : TypeToken<List<AppLayout>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveAppLayouts(layouts: List<AppLayout>) {
        Logger.d("STORAGE saveAppLayouts: count=${layouts.size}")
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_LAYOUTS] = gson.toJson(layouts)
        }
    }

    suspend fun addAppLayout(layout: AppLayout) {
        val current = getAppLayouts().first().toMutableList()
        current.add(layout)
        saveAppLayouts(current)
    }

    suspend fun updateAppLayout(layout: AppLayout) {
        val current = getAppLayouts().first().toMutableList()
        val index = current.indexOfFirst { it.id == layout.id }
        if (index >= 0) {
            current[index] = layout
            saveAppLayouts(current)
        }
    }

    suspend fun removeAppLayout(id: String) {
        val current = getAppLayouts().first().toMutableList()
        val beforeCount = current.size
        current.removeAll { it.id == id }
        Logger.d("STORAGE removeAppLayout: id=$id, before=$beforeCount, after=${current.size}")
        saveAppLayouts(current)
    }

    fun getLayoutsForOverlay(overlayId: String): Flow<List<AppLayout>> {
        return getAppLayouts().map { layouts ->
            layouts.filter { it.overlayId == overlayId }
        }
    }

    fun getLayoutForApp(overlayId: String, packageName: String): Flow<AppLayout?> {
        return getAppLayouts().map { layouts ->
            layouts.find { it.overlayId == overlayId && it.packageName == packageName && it.isEnabled }
        }
    }

    // ---- Settings ----

    fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[KEY_SETTINGS]
            val settings = if (json != null) {
                try {
                    gson.fromJson(json, AppSettings::class.java) ?: AppSettings()
                } catch (e: Exception) {
                    AppSettings()
                }
            } else {
                AppSettings()
            }
            // maxOverlays selalu dipaksa unlimited, tidak peduli nilai lama
            // yang mungkin masih tersimpan di storage (mis. dari versi lama
            // sebelum limit dihapus, di mana JSON tersimpan berisi
            // "maxOverlays":5).
            settings.copy(maxOverlays = Int.MAX_VALUE)
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETTINGS] = gson.toJson(settings)
        }
    }

    // ---- Clear ----

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
