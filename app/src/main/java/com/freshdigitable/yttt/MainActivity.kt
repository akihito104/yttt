package com.freshdigitable.yttt

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawerMenu = requireNotNull(findViewById<NavigationView>(R.id.main_navView))
        val drawer = requireNotNull(findViewById<DrawerLayout>(R.id.main_navLayout))
        setupDrawer(drawer, drawerMenu)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_navHost) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(navController.graph, drawer)
        setupActionBarWithNavController(navController, appBarConfiguration)
        drawerMenu.setupWithNavController(navController)
        this.appBarConfig = appBarConfiguration

        setupList()
    }

    private fun setupDrawer(
        drawer: DrawerLayout,
        drawerMenu: NavigationView,
    ) {
        val callback = onBackPressedDispatcher.addCallback(this) {
            if (drawer.isDrawerOpen(drawerMenu)) {
                drawer.close()
            }
        }
        drawer.addDrawerListener(object : SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                callback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                callback.isEnabled = false
            }
        })
        callback.isEnabled = drawer.isDrawerOpen(drawerMenu)
    }

    private var appBarConfig: AppBarConfiguration? = null

    override fun onSupportNavigateUp(): Boolean {
        val appBarConfiguration = this.appBarConfig ?: return super.onSupportNavigateUp()
        val navController = findNavController(R.id.main_navHost)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val navController = findNavController(R.id.main_navHost)
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        appBarConfig = null
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
