package com.freshdigitable.yttt.di

import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.local.YouTubeAccountLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@VisibleForTesting
@Module
@InstallIn(SingletonComponent::class)
interface YouTubeAccountDataSourceModule {
    companion object {
        @Singleton
        @Provides
        fun provideYouTubeAccountDataStoreLocal(
            dataStore: DataStore<Preferences>,
            coroutineScope: CoroutineScope,
        ): YouTubeAccountDataStore.Local = YouTubeAccountLocalDataSource(dataStore, coroutineScope)
    }

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(YouTube::class)
    fun bindAccountRepository(repository: YouTubeAccountRepository): AccountRepository
}
