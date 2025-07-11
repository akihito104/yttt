package com.freshdigitable.yttt.feature.oauth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.freshdigitable.yttt.LauncherOption
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.startLauncherActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TwitchOauthActivity : AppCompatActivity() {
    @Inject
    lateinit var twitchConsumer: TwitchOauthParser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeOAuthEvent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        logD { "onNewIntent: $intent" }
        super.onNewIntent(intent)
        consumeOAuthEvent(intent)
    }

    private fun consumeOAuthEvent(intent: Intent) {
        val url = intent.data?.toString() ?: return
        val res = twitchConsumer.consumeOAuthEvent(url)
        if (res) {
            startLauncherActivity(LauncherOption.ON_FINISH_OAUTH)
        } else {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
