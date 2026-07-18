package za.co.boardaf.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncDataStore by preferencesDataStore(name = "board_af_sync")

class AndroidBaselineStore(context: Context) : BaselineStore {
    private val appContext = context.applicationContext

    override suspend fun read(accountId: String): SyncBaselines =
        BaselineCodec.decode(appContext.syncDataStore.data.first()[key(accountId)])

    override suspend fun write(accountId: String, baselines: SyncBaselines) {
        appContext.syncDataStore.edit { preferences ->
            preferences[key(accountId)] = BaselineCodec.encode(baselines)
        }
    }

    private fun key(accountId: String) = stringPreferencesKey("baselines_v1_$accountId")
}
