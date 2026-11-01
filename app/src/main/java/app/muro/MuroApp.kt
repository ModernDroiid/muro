package app.muro

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.muro.data.ListRepository
import app.muro.data.MuroDb
import app.muro.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MuroApp : Application() {
    /** Para trabajo que debe sobrevivir a la pantalla y al servicio. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: MuroDb by lazy {
        Room.databaseBuilder(this, MuroDb::class.java, "muro.db").build()
    }
    val settings: Settings by lazy { Settings(this) }
    val lists: ListRepository by lazy { ListRepository(this, db.domains(), settings) }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            lists.seedIfEmpty()
            db.log().deleteOlderThan(System.currentTimeMillis() - LOG_RETENTION_MS)
        }
    }

    private companion object {
        val LOG_RETENTION_MS = TimeUnit.DAYS.toMillis(7)
    }
}

val Context.muro: MuroApp get() = applicationContext as MuroApp
