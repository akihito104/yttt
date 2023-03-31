package com.freshdigitable.yttt

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TimetableTabFragment : Fragment(R.layout.fragment_timetable_tab) {
    private val viewModel by activityViewModels<MainViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewPager = view.findViewById<ViewPager2>(R.id.main_pager)
        viewPager.adapter = ViewPagerAdapter(this)

        val tabLayout = requireNotNull(view.findViewById<TabLayout>(R.id.main_tabLayout))
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        TimetablePage.values().forEachIndexed { i, page ->
            page.bind(viewModel)
                .map { it.size }
                .distinctUntilChanged().observe(viewLifecycleOwner) { count ->
                    val tab = tabLayout.getTabAt(i)
                    tab?.text = page.tabText(view.context, count)
                }
        }

        val progress = view.findViewById<LinearProgressIndicator>(R.id.main_progress)
        viewModel.isLoading.observe(viewLifecycleOwner) {
            progress.visibility = if (it) View.VISIBLE else View.INVISIBLE
        }
    }
}

class ViewPagerAdapter(activity: Fragment) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = TimetablePage.values().size

    override fun createFragment(position: Int): Fragment = TimetableFragment.create(position)
}

enum class TimetablePage {
    OnAir {
        override fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>> = viewModel.onAir
        override fun tabText(context: Context, count: Int): String =
            context.getString(R.string.tab_onAir, count)
    },
    Upcoming {
        override fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>> = viewModel.upcoming
        override fun tabText(context: Context, count: Int): String =
            context.getString(R.string.tab_upcoming, count)
    },
    ;

    abstract fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>>
    abstract fun tabText(context: Context, count: Int): String
}
