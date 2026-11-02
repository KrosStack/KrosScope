package sample.tasktide.app

import android.app.Application
import android.provider.Settings
import sample.tasktide.app.config.FeatureFlags
import sample.tasktide.app.flavor.FlavorFlags
import sample.tasktide.core.model.TaskStore
import sample.tasktide.data.db.DriverFactory
import sample.tasktide.data.db.TaskRepository
import sample.tasktide.data.db.createTaskDatabase
import sample.tasktide.data.settings.PreferenceRepository
import sample.tasktide.data.settings.RoomPreferenceRepository
import sample.tasktide.data.settings.SettingsDatabase
import sample.tasktide.feature.settings.PreferenceRepositoryProvider
import sample.tasktide.feature.tasks.TaskStoreProvider
import sample.tasktide.sync.engine.SyncEngine
import sample.tasktide.sync.service.SyncEngineProvider

class TaskTideApp : Application(), TaskStoreProvider, SyncEngineProvider, PreferenceRepositoryProvider {
    val flags: FeatureFlags = FlavorFlags

    private val repository: TaskRepository by lazy { TaskRepository(createTaskDatabase(DriverFactory(this))) }

    override val taskStore: TaskStore
        get() = repository

    override val syncEngine: SyncEngine by lazy {
        SyncEngine(
            repository = repository,
            transport = AppSyncTransport(BuildConfig.SYNC_ENDPOINT, BuildConfig.VERBOSE_LOGGING),
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
        )
    }

    override val preferences: PreferenceRepository by lazy {
        RoomPreferenceRepository(SettingsDatabase.create(this).preferenceDao())
    }
}
