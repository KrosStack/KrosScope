package sample.room.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "item_name") val name: String,
)
