package sample.room.store

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Item::class], version = 1, exportSchema = true)
abstract class StoreDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
