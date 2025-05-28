package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.data.source.local.di.DbFlagModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.jetbrains.annotations.TestOnly
import javax.inject.Singleton

@TestOnly
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DbFlagModule::class],
)
interface InMemoryDbModule {
    companion object {
        @Provides
        @Singleton
        internal fun provideFlag(): DbFlagModule.Flag? = DbFlagModule.Flag.InMemory
    }
}
