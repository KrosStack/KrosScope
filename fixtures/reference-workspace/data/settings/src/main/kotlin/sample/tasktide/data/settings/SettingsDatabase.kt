package sample.tasktide.data.settings

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserPreference::class], version = 1, exportSchema = true)
abstract class SettingsDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        fun create(context: Context): SettingsDatabase =
            Room.databaseBuilder(context.applicationContext, SettingsDatabase::class.java, "settings.db").build()
    }
}
