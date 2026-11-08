package com.websnake.recentstv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** v1.0 stored settings in DataStore; read once to migrate, never written again. */
private val Context.legacyStore by preferencesDataStore(name = "recents_settings")

/**
 * Settings in plain SharedPreferences, with a process-wide in-memory copy.
 *
 * Why not DataStore: its first read in a fresh process cost 150–200 ms on the test
 * phone (class loading + proto parse), on the critical path of every cold open.
 * SharedPreferences starts loading on a background thread as soon as it is first
 * asked for (RecentsApp does that at process start) and is ready long before the
 * overlay needs it.
 */
class SettingsStore(private val context: Context) {

    companion object {
        private const val FILE = "recents_settings_v2"
        private const val MIGRATED = "_migrated_v1"
        @Volatile private var cached: Settings? = null
        private val bools = setOf(SettingsKeys.SHOW_KILL_ALL, SettingsKeys.FORCE_STOP)
        private val ints = setOf(SettingsKeys.BG_DIM)
        private val sets = setOf(SettingsKeys.EXCEPTIONS)

        /** Begin loading the file off the main thread (call at process start). */
        fun warm(context: Context) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(): Settings = SettingsCodec.decode { k -> prefs.all[k] }.also { cached = it }

    suspend fun current(): Settings = cached ?: withContext(Dispatchers.IO) {
        migrateIfNeeded()
        read()
    }

    /** Emits the current settings, then every change (Settings screen). */
    val settings: Flow<Settings> = callbackFlow {
        withContext(Dispatchers.IO) { migrateIfNeeded() }
        trySend(read())
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    suspend fun save(s: Settings) {
        cached = s
        withContext(Dispatchers.IO) { write(s).commit() }
    }

    suspend fun update(transform: (Settings) -> Settings) = save(transform(current()))

    private fun write(s: Settings): SharedPreferences.Editor {
        val e = prefs.edit()
        for ((k, v) in SettingsCodec.encode(s)) {
            @Suppress("UNCHECKED_CAST")
            when (k) {
                in bools -> e.putBoolean(k, v as Boolean)
                in ints -> e.putInt(k, v as Int)
                in sets -> e.putStringSet(k, HashSet(v as Set<String>))
                else -> e.putString(k, v as String)
            }
        }
        return e
    }

    /** One time only: carry v1.0's DataStore settings over (only if that file exists). */
    private suspend fun migrateIfNeeded() {
        if (prefs.getBoolean(MIGRATED, false)) return
        val legacyFile = File(context.filesDir, "datastore/recents_settings.preferences_pb")
        if (legacyFile.isFile) {
            val p = runCatching { context.legacyStore.data.first() }.getOrNull()
            if (p != null) write(decodeLegacy(p)).putBoolean(MIGRATED, true).commit()
            else prefs.edit().putBoolean(MIGRATED, true).commit()
        } else {
            prefs.edit().putBoolean(MIGRATED, true).commit()
        }
    }

    private fun decodeLegacy(p: Preferences): Settings = SettingsCodec.decode { k ->
        when (k) {
            in bools -> p[booleanPreferencesKey(k)]
            in ints -> p[intPreferencesKey(k)]
            in sets -> p[stringSetPreferencesKey(k)]
            else -> p[stringPreferencesKey(k)]
        }
    }
}
