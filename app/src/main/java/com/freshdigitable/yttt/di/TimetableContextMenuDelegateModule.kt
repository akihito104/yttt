package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.TimetableContextMenuDelegate
import com.freshdigitable.yttt.TimetableContextMenuDelegateForTwitch
import com.freshdigitable.yttt.TimetableContextMenuDelegateForYouTube
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.YouTubeVideo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
interface TimetableContextMenuDelegateModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeVideo.Id::class)
    fun bindTimetableContextMenuDelegateForYouTube(delegate: TimetableContextMenuDelegateForYouTube): TimetableContextMenuDelegate.MenuSelector

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchStream.Id::class)
    fun bindTimetableContextMenuDelegateForTwitch(delegate: TimetableContextMenuDelegateForTwitch): TimetableContextMenuDelegate.MenuSelector

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchChannelSchedule.Stream.Id::class)
    fun bindTimetableContextMenuDelegateForTwitchChannelSchedule(delegate: TimetableContextMenuDelegateForTwitch): TimetableContextMenuDelegate.MenuSelector
}
