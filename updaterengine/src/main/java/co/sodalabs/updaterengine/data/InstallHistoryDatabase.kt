package co.sodalabs.updaterengine.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Deprecated("This is an example for Room")
@Database(
    // Version history:
    // 1 -  Initial design.
    version = 1,
    entities = [InstallRecord::class],
    exportSchema = false
)
abstract class InstallHistoryDatabase : RoomDatabase() {

    abstract fun getInstallHistoryDao(): InstallHistoryDao
}