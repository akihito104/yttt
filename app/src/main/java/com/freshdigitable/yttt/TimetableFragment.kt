package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.freshdigitable.yttt.compose.TimetableScreen
import com.google.accompanist.themeadapter.material.MdcTheme

class TimetableFragment : Fragment() {
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
                    TimetableScreen(
                        page = TimetablePage.values()[position],
                        viewModel = viewModel,
                        navController = findNavController(),
                    )
                }
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
