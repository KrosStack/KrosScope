package sample.tasktide.data.settings

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preference")
data class UserPreference(
    @PrimaryKey val name: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
