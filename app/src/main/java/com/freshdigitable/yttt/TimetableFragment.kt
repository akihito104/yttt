package com.freshdigitable.yttt

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.freshdigitable.yttt.compose.LiveVideoListItemView
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.accompanist.themeadapter.material.MdcTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TimetableFragment : Fragment(R.layout.fragment_timetable) {
    private val viewModel by activityViewModels<MainViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val timetableAdapter = TimetableAdapter()
        view.findViewById<RecyclerView>(R.id.timetable_list).apply {
            adapter = timetableAdapter
            layoutManager = LinearLayoutManager(context)
        }
        TimetablePage.values()[position].bind(viewModel).observe(viewLifecycleOwner) {
            timetableAdapter.submitList(it)
        }
        view.findViewById<SwipeRefreshLayout>(R.id.timetable_swipeRefreshLayout).apply {
            viewModel.isLoading.observe(viewLifecycleOwner) {
                this.isRefreshing = it
            }
            setOnRefreshListener {
                viewModel.loadList()
            }
        }
    }

    private val position: Int
        get() = requireArguments().getInt(ARGS_POSITION)

    companion object {
        private const val ARGS_POSITION = "key_position"

        fun create(position: Int): TimetableFragment {
            return TimetableFragment().apply {
                arguments = bundleOf(ARGS_POSITION to position)
            }
        }
    }
}

class TimetableAdapter : ListAdapter<LiveVideo, VideoViewHolder>(diffUtil) {
    companion object {
        private val diffUtil = object : DiffUtil.ItemCallback<LiveVideo>() {
            override fun areItemsTheSame(oldItem: LiveVideo, newItem: LiveVideo): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LiveVideo, newItem: LiveVideo): Boolean =
                oldItem == newItem
        }
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd(E) HH:mm")
        private val Instant.toLocalFormattedText: String
            get() {
                val localDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
                return localDateTime.format(dateTimeFormatter)
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = ComposeView(parent.context)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener {
            val action =
                TimetableTabFragmentDirections.actionTttFragmentToVideoDetailFragment(item.id.value)
            it.findNavController().navigate(action)
        }
        holder.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    LiveVideoListItemView(video = item)
                }
            }
        }
    }
}

class VideoViewHolder(view: View) : ViewHolder(view) {
    val composeView: ComposeView = view as ComposeView
}
