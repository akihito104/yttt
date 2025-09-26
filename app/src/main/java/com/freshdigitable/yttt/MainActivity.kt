package com.freshdigitable.yttt

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.freshdigitable.yttt.LauncherOption.Companion.toLauncherOption
import com.freshdigitable.yttt.compose.RootScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logD { "onCreate($this): ${intent.data}" }
        setContent {

            RootScreen(launcherOption = intent.toLauncherOption())
        }
    }
}
