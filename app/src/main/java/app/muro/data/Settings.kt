package app.muro.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.store by preferencesDataStore("settings")

data class UserSettings(
    val protectionEnabled: Boolean = false,
    val selectedApps: Set<String> = emptySet(),
    val adsList: Boolean = true,
    val trackersList: Boolean = true,
    val customList: Boolean = false,
    val startOnBoot: Boolean = true,
    val backupDns: String = "1.1.1.1",
    val listsUpdatedAt: Long = 0,
) {
    val enabledLists: List<ListId>
        get() = buildList {
            if (adsList) add(ListId.ADS)
            if (trackersList) add(ListId.TRACKERS)
            if (customList) add(ListId.CUSTOM)
        }
}

class Settings(context: Context) {
    private val store = context.applicationContext.store

    val flow: Flow<UserSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val defaults = UserSettings()
            UserSettings(
                protectionEnabled = prefs[PROTECTION] ?: defaults.protectionEnabled,
                selectedApps = prefs[SELECTED_APPS] ?: defaults.selectedApps,
                adsList = prefs[ADS] ?: defaults.adsList,
                trackersList = prefs[TRACKERS] ?: defaults.trackersList,
                customList = prefs[CUSTOM] ?: defaults.customList,
                startOnBoot = prefs[BOOT] ?: defaults.startOnBoot,
                backupDns = prefs[BACKUP_DNS] ?: defaults.backupDns,
                listsUpdatedAt = prefs[LISTS_UPDATED] ?: defaults.listsUpdatedAt,
            )
        }

    suspend fun setProtectionEnabled(value: Boolean) = set(PROTECTION, value)
    suspend fun setStartOnBoot(value: Boolean) = set(BOOT, value)
    suspend fun setBackupDns(value: String) = set(BACKUP_DNS, value)
    suspend fun setListsUpdatedAt(value: Long) = set(LISTS_UPDATED, value)

    suspend fun setListEnabled(list: ListId, value: Boolean) = set(
        when (list) {
            ListId.ADS -> ADS
            ListId.TRACKERS -> TRACKERS
            ListId.CUSTOM -> CUSTOM
        },
        value,
    )

    suspend fun setAppSelected(packageName: String, selected: Boolean) {
        store.edit { prefs ->
            val current = prefs[SELECTED_APPS].orEmpty()
            prefs[SELECTED_APPS] = if (selected) current + packageName else current - packageName
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        val ADS = booleanPreferencesKey("list_ads")
        val TRACKERS = booleanPreferencesKey("list_trackers")
        val CUSTOM = booleanPreferencesKey("list_custom")
        val BOOT = booleanPreferencesKey("start_on_boot")
        val BACKUP_DNS = stringPreferencesKey("backup_dns")
        val LISTS_UPDATED = longPreferencesKey("lists_updated_at")
    }
}
