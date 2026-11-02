package sample.tasktide.data.settings

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM user_preference ORDER BY name")
    fun observeAll(): Flow<List<UserPreference>>

    @Query("SELECT * FROM user_preference WHERE name = :name")
    suspend fun find(name: String): UserPreference?

    @Upsert
    suspend fun upsert(preference: UserPreference)

    @Query("DELETE FROM user_preference WHERE name = :name")
    suspend fun delete(name: String)
}
