package com.freshdigitable.yttt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import java.math.BigInteger
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ChannelFragment : Fragment(R.layout.fragment_channel) {
    private val viewModel: ChannelViewModel by viewModels()
    private val args: ChannelFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val banner = view.findViewById<ImageView>(R.id.channel_banner)
        val icon = view.findViewById<ImageView>(R.id.channel_icon)
        val name = view.findViewById<TextView>(R.id.channel_name)
        val stats = view.findViewById<TextView>(R.id.channel_stats)
        val id = LiveChannel.Id(args.channelId)
        viewModel.fetchChannel(id).observe(viewLifecycleOwner) {
            if (it == null) {
                return@observe
            }
            if (it.bannerUrl?.isNotBlank() == true) {
                Glide.with(banner)
                    .load(it.bannerUrl)
                    .transform(CustomCrop(width = 1253, height = 338))
                    .into(banner)
            } else {
                banner.visibility = View.GONE
            }
            Glide.with(icon)
                .load(it.iconUrl)
                .into(icon)
            name.text = it.title
            stats.text = "${it.customUrl} " +
                (if (!it.isSubscriberHidden) "Subscribers:${it.subscriberCount.toStringWithUnitPrefix} " else "") +
                "Videos:${it.videoCount} Views:${it.viewsCount.toStringWithComma} " +
                "Published:${it.publishedAt.toLocalFormattedText}"
        }
        val tabLayout = view.findViewById<TabLayout>(R.id.channel_tab)
        val viewPager = view.findViewById<ViewPager2>(R.id.channel_pager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = ChannelPage.values().size

            override fun createFragment(position: Int): Fragment =
                ChannelPageFragment.create(position, id)
        }
        TabLayoutMediator(tabLayout, viewPager) { tab, i ->
            tab.text = ChannelPage.values()[i].name
        }.attach()
    }

    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        private val Instant.toLocalFormattedText: String
            get() {
                val localDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
                return localDateTime.format(dateTimeFormatter)
            }
        private val BigInteger.toStringWithComma: String
            get() = NumberFormat.getNumberInstance(Locale.US).format(this)
        private val BigInteger.toStringWithUnitPrefix: String
            get() {
                val decimal = this.toBigDecimal()
                val precision = decimal.precision()
                return if (precision <= 3) {
                    this.toString()
                } else {
                    val prefixGrade = (precision - 1) / 3
                    val shift = prefixGrade * 3
                    val digit = DecimalFormat("#.##").format(decimal.movePointLeft(shift))
                    "${digit}${unitPrefix[prefixGrade]}"
                }
            }
        private val unitPrefix = arrayOf("", "k", "M", "G", "T", "P", "E")
    }
}

@AndroidEntryPoint
class ChannelPageFragment : Fragment(R.layout.fragment_channel_page) {
    private val viewModel: ChannelViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val textView = view.findViewById<TextView>(R.id.channelPage_text)
        page.bind(viewModel, channelId).observe(viewLifecycleOwner) {
            textView.text = it
        }
    }

    private val page: ChannelPage
        get() {
            val position = requireArguments().getInt(ARGS_POSITION)
            return ChannelPage.values()[position]
        }
    private val channelId: LiveChannel.Id
        get() {
            val id = requireNotNull(requireArguments().getString(ARGS_CHANNEL_ID))
            return LiveChannel.Id(id)
        }

    companion object {
        private const val ARGS_POSITION = "position"
        private const val ARGS_CHANNEL_ID = "channelId"
        fun create(position: Int, id: LiveChannel.Id): ChannelPageFragment {
            val args = bundleOf(
                ARGS_POSITION to position,
                ARGS_CHANNEL_ID to id.value
            )
            return ChannelPageFragment().apply {
                arguments = args
            }
        }
    }
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : ViewModel() {
    fun fetchChannel(id: LiveChannel.Id): LiveData<LiveChannelDetail?> = flow {
        val channel = repository.fetchChannelList(listOf(id)).firstOrNull()
        emit(channel)
    }.asLiveData(viewModelScope.coroutineContext)

    fun fetchChannelSection(id: LiveChannel.Id): LiveData<List<LiveChannelSection>> = flow {
        val channelSection = repository.fetchChannelSection(id)
        emit(channelSection)
    }.asLiveData(viewModelScope.coroutineContext)
}

private class CustomCrop(
    private val width: Int,
    private val height: Int
) : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val scaled = toTransform.width / 2048.0f
        val w = (width * scaled).toInt()
        val h = (height * scaled).toInt()
        val dx = (w - toTransform.width) * 0.5f
        val dy = (h - toTransform.height) * 0.5f
        val matrix = Matrix().apply {
            postTranslate(dx, dy)
        }
        val bitmap = pool.get(w, h, toTransform.config ?: Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(toTransform.hasAlpha())
        }
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(toTransform, matrix, Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.setBitmap(null)
        return bitmap
    }

    override fun equals(other: Any?): Boolean = other is CustomCrop

    override fun hashCode(): Int = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) = messageDigest.update(ID_BYTES)

    companion object {
        private val ID = checkNotNull(CustomCrop::class.java.canonicalName)
        private val ID_BYTES = ID.toByteArray(CHARSET)
    }
}

enum class ChannelPage {
    ABOUT {
        override fun bind(viewModel: ChannelViewModel, id: LiveChannel.Id): LiveData<String> =
            viewModel.fetchChannel(id).map { it?.description ?: "" }
    },
    DEBUG_CHANNEL {
        override fun bind(viewModel: ChannelViewModel, id: LiveChannel.Id): LiveData<String> =
            viewModel.fetchChannel(id).map { it.toString() }
    },
    DEBUG_CHANNEL_SECTION {
        override fun bind(viewModel: ChannelViewModel, id: LiveChannel.Id): LiveData<String> =
            viewModel.fetchChannelSection(id).map { it.toString() }
    }
    ;

    abstract fun bind(viewModel: ChannelViewModel, id: LiveChannel.Id): LiveData<String>
}
