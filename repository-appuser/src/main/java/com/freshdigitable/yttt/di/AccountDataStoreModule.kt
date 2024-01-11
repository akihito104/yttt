package com.freshdigitable.yttt.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.local.TwitchAccountLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeAccountLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal interface AccountDataStoreModule {
    companion object {
        private const val PREF_FILENAME = "yttt"

        @Provides
        fun provideSharedPreference(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_FILENAME, Context.MODE_PRIVATE)

        @Provides
        fun provideDataStore(
            @ApplicationContext context: Context,
            prefs: SharedPreferences,
        ): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration({ prefs })),
            ) {
                context.preferencesDataStoreFile("settings")
            }
        }

        @Provides
        fun provideIoCoroutineScope(): CoroutineScope =
            CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @Binds
    fun bindYouTubeAccountLocalDataSource(source: YouTubeAccountLocalDataSource): YouTubeAccountDataStore.Local

    @Binds
    fun bindTwitchAccountLocalDataSource(source: TwitchAccountLocalDataSource): TwitchAccountDataStore.Local
}
