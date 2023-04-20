package com.freshdigitable.yttt

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.freshdigitable.yttt.compose.MainScreen
import com.google.accompanist.themeadapter.material.MdcTheme
import com.google.android.gms.common.ConnectionResult
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MdcTheme {
                MainScreen()
            }
        }
        setupList()
    }

    private fun setupList() {
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
