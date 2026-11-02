package sample.room.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE id = :id")
    fun find(id: Long): Item?

    @Insert
    fun insert(item: Item)
}
