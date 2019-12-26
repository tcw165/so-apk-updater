package co.sodalabs.updaterengine.data

import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import co.sodalabs.updaterengine.data.InstallRecord.Companion.generateID

@Deprecated("This is an example for Room")
@Entity(tableName = "install_history_table")
data class InstallRecord(
    /**
     * See [generateID].
     */
    @PrimaryKey
    @ColumnInfo(name = "id") // e.g. "APP_UPDATE-0.1.0-0.1.1"
    val id: String,
    @NonNull
    @ColumnInfo(name = "created_epoch")
    val createdTimeEpoch: Long,
    @NonNull
    @ColumnInfo(name = "created_epoch_formatted")
    val createdTimeEpochFormatted: String,
    @NonNull
    @ColumnInfo(name = "updated_epoch")
    val updatedTimeEpoch: Long,
    @NonNull
    @ColumnInfo(name = "updated_epoch_formatted")
    val updatedTimeEpochFormatted: String,
    @NonNull
    @ColumnInfo(name = "install_attempts")
    val installAttempts: Int,
    @NonNull
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @NonNull
    @ColumnInfo(name = "download_url")
    val downloadURL: String,
    @ColumnInfo(name = "blocked_by")
    val blockedBy: String? = null
) {

    companion object {

        fun generateID(
            type: Type,
            fromVersion: String,
            toVersion: String
        ): String {
            return "$type-$fromVersion-$toVersion"
        }
    }

    @Keep
    enum class Type {
        FW_INCREMENTAL_UPDATE,
        FW_FULL_UPDATE,
        APP_UPDATE;

        companion object {
            fun fromName(
                name: String
            ): Type {
                return valueOf(name)
            }
        }
    }
}