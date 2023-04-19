package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.navigation.findNavController
import com.freshdigitable.yttt.compose.TimetableTabScreen
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.accompanist.themeadapter.material.MdcTheme

class TimetableTabFragment : Fragment() {
    private val viewModel by activityViewModels<MainViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    TimetableTabScreen(
                        viewModel = viewModel,
                        onListItemClicked = { id ->
                            val action = TimetableTabFragmentDirections
                                .actionTttFragmentToVideoDetailFragment(id.value)
                            findNavController().navigate(action)
                        },
                    )
                }
            }
        }
    }
}

enum class TimetablePage {
    OnAir {
        override val textRes: Int = R.string.tab_onAir
        override fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>> = viewModel.onAir
    },
    Upcoming {
        override val textRes: Int = R.string.tab_upcoming
        override fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>> = viewModel.upcoming
    },
    ;

    abstract fun bind(viewModel: MainViewModel): LiveData<List<LiveVideo>>
    abstract val textRes: Int
}
