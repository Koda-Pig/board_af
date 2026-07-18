package za.co.boardaf.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.boardDataStore by preferencesDataStore(name = "board_af_store")

class AndroidSnapshotIO(context: Context) : SnapshotIO {
    private val appContext = context.applicationContext

    override suspend fun readSnapshot(): String? =
        appContext.boardDataStore.data.first()[SNAPSHOT_KEY]

    override suspend fun writeSnapshot(value: String) {
        appContext.boardDataStore.edit { preferences -> preferences[SNAPSHOT_KEY] = value }
    }

    override suspend fun writeCorruptBackup(value: String) {
        appContext.boardDataStore.edit { preferences ->
            preferences[stringPreferencesKey("snapshot_v2_corrupt_${System.currentTimeMillis()}")] = value
        }
    }

    override suspend fun readLegacyProblems(): String? = legacyPreferences().getString("problems_v1", null)

    override suspend fun readLegacyGradeSystem(): String? = legacyPreferences().getString("grade_system", null)

    private fun legacyPreferences() =
        appContext.getSharedPreferences("board_af", Context.MODE_PRIVATE)

    private companion object {
        val SNAPSHOT_KEY = stringPreferencesKey("snapshot_v2")
    }
}
