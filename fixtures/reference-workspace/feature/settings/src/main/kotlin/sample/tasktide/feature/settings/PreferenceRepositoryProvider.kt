package sample.tasktide.feature.settings

import sample.tasktide.data.settings.PreferenceRepository

interface PreferenceRepositoryProvider {
    val preferences: PreferenceRepository
}
