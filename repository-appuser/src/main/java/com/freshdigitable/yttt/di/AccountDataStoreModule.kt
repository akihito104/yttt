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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface AccountDataStoreModule {
    companion object {
        private const val PREF_FILENAME = "yttt"

        @Provides
        @Singleton
        fun provideSharedPreference(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_FILENAME, Context.MODE_PRIVATE)

        @Singleton
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
    }
}
