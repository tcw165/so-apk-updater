@file:Suppress("unused")

package co.sodalabs.updaterengine.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Deprecated("This is an example for Room")
@Dao
interface InstallHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putInstallRecord(record: InstallRecord)

    @Query("SELECT * from install_history_table WHERE id = :id")
    fun getInstallRecord(id: String): InstallRecord?

    @Query("SELECT * from install_history_table ORDER BY created_epoch DESC")
    fun getAllInstallRecords(): List<InstallRecord>

    @Query("DELETE from install_history_table WHERE id NOT IN (SELECT id from install_history_table ORDER BY created_epoch DESC LIMIT :capacity)")
    fun pruneOldInstallRecords(capacity: Int)

    @Query("DELETE from install_history_table")
    fun deleteAllInstallRecords()

    // TODO: Dive into the source code to see if this Observable emits the initial value.
    // @Query("SELECT * from install_history_table ORDER BY created_epoch DESC")
    // fun observeAllInstallRecords(): Observable<List<InstallRecord>>
}