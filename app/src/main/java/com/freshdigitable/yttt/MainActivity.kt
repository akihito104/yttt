package com.freshdigitable.yttt

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.android.gms.common.ConnectionResult
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val viewPager = findViewById<ViewPager2>(R.id.main_pager)
        viewPager.adapter = ViewPagerAdapter(this)

        val tabLayout = requireNotNull(findViewById<TabLayout>(R.id.main_tabLayout))
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        TimetablePage.values().forEachIndexed { i, page ->
            page.bind(viewModel)
                .map { it.size }
                .distinctUntilChanged().observe(this) { count ->
                    val tab = tabLayout.getTabAt(i)
                    tab?.text = page.tabText(this, count)
                }
        }

        setup()
    }

    private fun setup() {
        val statusCode = viewModel.getConnectionStatus()
        if (statusCode != ConnectionResult.SUCCESS) {
            if (viewModel.isUserResolvableError(statusCode)) {
                showGoogleApiErrorDialog(statusCode)
            }
            return
        }
        if (!viewModel.hasAccount()) {
            pickAccount()
        } else {
            loadTimeline()
        }
    }

    private fun pickAccount() {
        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.GET_ACCOUNTS
        )
        when {
            permissionStatus == PackageManager.PERMISSION_GRANTED -> startPickAccount()
            shouldShowRequestPermissionRationale(android.Manifest.permission.GET_ACCOUNTS) -> {
                TODO("")
            }
            else -> getAccountRequestPermission.launch(android.Manifest.permission.GET_ACCOUNTS)
        }
    }

    private fun loadTimeline() {
        val loggedIn = viewModel.login()
        check(loggedIn) { "login failure..." }
        viewModel.loadList()
    }

    private val getAccountRequestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                startPickAccount()
            }
        }

    private val accountPicker =
        registerForActivityResult(object : ActivityResultContract<Unit, String>() {
            override fun createIntent(context: Context, input: Unit): Intent =
                viewModel.createPickAccountIntent()

            override fun parseResult(resultCode: Int, intent: Intent?): String {
                if (resultCode == RESULT_OK && intent != null && intent.extras != null) {
                    return intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: ""
                }
                return ""
            }
        }) {
            if (it.isNotEmpty()) {
                viewModel.login(it)
                viewModel.loadList()
            }
        }

    private fun startPickAccount() {
        accountPicker.launch(Unit)
    }

    private fun showGoogleApiErrorDialog(
        connectionStatusCode: Int
    ) {
        val contract = ActivityResultContracts.StartIntentSenderForResult()
        val googleApiErrorDialog = registerForActivityResult(contract) {
            if (it.resultCode == RESULT_OK) {
                if (!viewModel.hasAccount()) {
                    pickAccount()
                } else {
                    loadTimeline()
                }
            }
        }
        viewModel.googleApiAvailability.showErrorDialogFragment(
            this,
            connectionStatusCode,
            googleApiErrorDialog,
            null,
        )
    }
}

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
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
