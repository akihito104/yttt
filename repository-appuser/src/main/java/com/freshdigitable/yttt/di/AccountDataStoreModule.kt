package com.freshdigitable.yttt.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface AccountDataStoreModule {
    companion object {
        private const val PREF_FILENAME = "yttt"

        @Provides
        fun provideSharedPreference(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_FILENAME, Context.MODE_PRIVATE)

        @Provides
        fun provideDataStore(ds: DataStoreImpl): DataStore<Preferences> = ds.dataStore

        @Provides
        fun provideIoCoroutineScope(): CoroutineScope =
            CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}

@Singleton
internal class DataStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
    prefs: SharedPreferences,
) {
    val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration({ prefs })),
        ) {
            context.preferencesDataStoreFile("settings")
        }
    }
}
