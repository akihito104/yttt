package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.di.DateTimeModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Duration
import java.time.Instant
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DateTimeModule::class],
)
interface FakeDateTimeProviderModule {
    companion object {
        var instant: Instant? = null
            set(value) {
                field = value
                if (field != null) onTimeAdvanced(checkNotNull(field))
            }
        var onTimeAdvanced: (Instant) -> Unit = {}

        fun clear() {
            onTimeAdvanced = {}
            instant = null
        }

        @Provides
        @Singleton
        fun provideDateTimeProvider(): DateTimeProvider = object : DateTimeProvider {
            override fun now(): Instant = checkNotNull(instant)
        }
    }
}

fun CacheControl.Companion.zero(): CacheControl = CacheControl.create(Instant.EPOCH, Duration.ZERO)
