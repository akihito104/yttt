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
import com.freshdigitable.yttt.data.model.LiveVideo

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

class TimetableAdapter : ListAdapter<LiveVideo, VideoViewHolder>(diffUtil) {
    companion object {
        private val diffUtil = object : DiffUtil.ItemCallback<LiveVideo>() {
            override fun areItemsTheSame(oldItem: LiveVideo, newItem: LiveVideo): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LiveVideo, newItem: LiveVideo): Boolean =
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
        holder.title.text = item.title
        holder.channelTitle.text = item.channel.title
        holder.dateTime.text = item.scheduledStartDateTime?.toString() ?: "none"
    }
}

class VideoViewHolder(view: View) : ViewHolder(view) {
    val title: TextView = view.findViewById(R.id.video_title)
    val channelTitle: TextView = view.findViewById(R.id.video_channelName)
    val dateTime: TextView = view.findViewById(R.id.video_startDateTime)
}
