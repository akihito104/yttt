package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.compose.statsText
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.feature.timetable.TimeAdjustment
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class LiveVideoDetailItemTest : ShouldSpec(
    {
        val commonZoneId = ZoneId.of("Asia/Tokyo")
        val commonLocale = Locale.JAPAN

        context("statsText") {
            val params = listOf(
                StatsTextParam(
                    dateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = BigInteger.ZERO,
                    contentType = TimetablePage.OnAir,
                    expected = "Started:2025/01/12(日) 19:00:00・Viewers:0",
                    name = "with viewers",
                ),
                StatsTextParam(
                    dateTime = Instant.parse("2025-01-12T01:59:00.000+09:00"),
                    extraHourOfDay = 2,
                    viewerCount = BigInteger.ZERO,
                    contentType = TimetablePage.OnAir,
                    expected = "Started:2025/01/11(土) 25:59:00・Viewers:0",
                    name = "extraHoursOfDay:2",
                ),
                StatsTextParam(
                    dateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = null,
                    contentType = TimetablePage.OnAir,
                    expected = "Started:2025/01/12(日) 19:00:00",
                    name = "without viewers",
                ),
                StatsTextParam(
                    dateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = BigInteger.ZERO,
                    contentType = TimetablePage.Upcoming,
                    expected = "Starting:2025/01/12(日) 19:00・Viewers:0",
                    name = "with viewers",
                ),
                StatsTextParam(
                    dateTime = Instant.parse("2025-01-12T02:59:00.000+09:00"),
                    extraHourOfDay = 3,
                    viewerCount = BigInteger.ZERO,
                    contentType = TimetablePage.Upcoming,
                    expected = "Starting:2025/01/11(土) 26:59・Viewers:0",
                    name = "with viewers",
                ),
                StatsTextParam(
                    dateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = null,
                    contentType = TimetablePage.Upcoming,
                    expected = "Starting:2025/01/12(日) 19:00",
                    name = "without viewers",
                ),
            )
            withData(
                nameFn = { param -> "given ${param.name}, then actual string is '''${param.expected}'''" },
                params,
            ) { param ->
                // setup
                val sut = DetailItem(
                    LiveVideoDetailImpl(param),
                    TimeAdjustment(Duration.ofHours(param.extraHourOfDay), commonZoneId, commonLocale),
                )
                // exercise
                val actual = sut.statsText()
                // verify
                actual shouldBe param.expected
            }
        }
    },
)

internal data class StatsTextParam(
    val dateTime: Instant,
    val viewerCount: BigInteger?,
    val contentType: TimetablePage,
    val extraHourOfDay: Long = 0,
    val expected: String,
    val name: String,
)

private class LiveVideoDetailImpl(private val param: StatsTextParam) : LiveVideoDetail {
    override val dateTime: Instant get() = param.dateTime
    override val viewerCount: BigInteger? get() = param.viewerCount
    override val contentType: TimetablePage get() = param.contentType
    override val id: LiveVideo.Id get() = LiveVideo.Id("id", YouTubeVideo.Id::class)
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = LiveChannel.Id("id", YouTubeVideo.Id::class),
            title = "title",
            iconUrl = "",
            platform = YouTube,
        )
    override val thumbnailUrl: String get() = ""
    override val title: AnnotatableString get() = AnnotatableString.create("") { emptyList() }
    override val description: AnnotatableString get() = AnnotatableString.create("") { emptyList() }
}
