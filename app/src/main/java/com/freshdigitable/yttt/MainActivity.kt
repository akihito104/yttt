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
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.tabs.TabLayoutMediator
import com.google.api.services.youtube.model.Video

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel> { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val viewPager = findViewById<ViewPager2>(R.id.main_pager)
        viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(findViewById(R.id.main_tabLayout), viewPager) { tab, pos ->
            tab.text = TimetablePage.values()[pos].name
        }.attach()

        setup()
    }

    private fun setup() {
        val googleService = (application as YtttApp).googleService
        val statusCode = googleService.connectionStatusCode
        if (statusCode == ConnectionResult.SUCCESS) {
            val hasAccount = checkAccount()
            if (hasAccount) {
                viewModel.onInit()
            }
        } else {
            if (googleService.googleApiAvailability.isUserResolvableError(statusCode)) {
                showGoogleApiErrorDialog(statusCode)
            }
        }
    }

    private fun checkAccount(): Boolean {
        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.GET_ACCOUNTS
        )
        when {
            permissionStatus == PackageManager.PERMISSION_GRANTED -> {
                val succeeded = viewModel.login()
                if (succeeded) {
                    return true
                } else {
                    startPickAccount()
                }
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.GET_ACCOUNTS) -> {
                TODO("")
            }
            else -> getAccountRequestPermission.launch(android.Manifest.permission.GET_ACCOUNTS)
        }
        return false
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
                viewModel.onInit()
            }
        }

    private fun startPickAccount() {
        accountPicker.launch(Unit)
    }

    private fun showGoogleApiErrorDialog(
        connectionStatusCode: Int
    ) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val googleApiErrorDialog =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == RESULT_OK) {
                    val hasAccount = checkAccount()
                    if (hasAccount) {
                        viewModel.onInit()
                    }
                }
            }
        apiAvailability.showErrorDialogFragment(
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
        override fun bind(viewModel: MainViewModel): LiveData<List<Video>> = viewModel.onAir
    },
    Next {
        override fun bind(viewModel: MainViewModel): LiveData<List<Video>> = viewModel.next
    },
    ;

    abstract fun bind(viewModel: MainViewModel): LiveData<List<Video>>
}
