package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.api.services.youtube.model.Video

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

class TimetableAdapter : ListAdapter<Video, VideoViewHolder>(diffUtil) {
    companion object {
        private val diffUtil = object : DiffUtil.ItemCallback<Video>() {
            override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.view_video_item, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.snippet.title
        holder.channelTitle.text = item.snippet.channelTitle
        holder.dateTime.text =
            item.liveStreamingDetails?.scheduledStartTime?.toStringRfc3339() ?: "none"
    }
}

class VideoViewHolder(view: View) : ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.video_title)
    val channelTitle: TextView = view.findViewById(R.id.video_channelName)
    val dateTime: TextView = view.findViewById(R.id.video_startDateTime)
}
