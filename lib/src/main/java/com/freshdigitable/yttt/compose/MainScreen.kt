package com.freshdigitable.yttt.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.navigation.NavActivity
import com.freshdigitable.yttt.compose.navigation.NavR
import com.freshdigitable.yttt.compose.navigation.composableWith
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.lib.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    MainScreen(
        navController = navController,
        navigation = viewModel.navigation,
        startDestination = viewModel.startDestination,
        onDrawerMenuClick = {
            val route = viewModel.getDrawerRoute(it)
            navController.navigate(route)
        },
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScreen(
    navController: NavHostController = rememberNavController(),
    navigation: Set<NavR>,
    startDestination: String,
    onDrawerMenuClick: (DrawerMenuItem) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerImpl(onClicked = {
                onDrawerMenuClick(it)
                coroutineScope.launch {
                    drawerState.close()
                }
            })
        },
    ) {
        Scaffold(
            topBar = {
                val backStack = navController.currentBackStackEntryAsState()
                TopAppBarImpl(
                    currentBackStackEntryProvider = { backStack.value },
                    onMenuIconClicked = {
                        coroutineScope.launch {
                            drawerState.open()
                        }
                    },
                    onUpClicked = { navController.navigateUp() },
                )
            },
        ) { padding ->
            SharedTransitionLayout {
                NavHost(
                    modifier = Modifier.padding(padding),
                    navController = navController,
                    startDestination = startDestination,
                ) {
                    composableWith(navController = navController, navRoutes = navigation)
                    composableWith(
                        navController,
                        LiveVideoSharedTransitionRoute.routes,
                        this@SharedTransitionLayout,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopAppBarImpl(
    currentBackStackEntryProvider: () -> NavBackStackEntry?,
    onMenuIconClicked: () -> Unit,
    onUpClicked: () -> Unit,
) {
    val backStack = currentBackStackEntryProvider()
    val navRoute = MainNavRoute.routes.find { it.route == backStack?.destination?.route }
    val title = navRoute?.title(backStack?.arguments)
    TopAppBarImpl(
        title = title,
        icon = {
            val route = backStack?.destination?.route
            if (backStack == null || route == LiveVideoSharedTransitionRoute.TimetableTab.route) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = onMenuIconClicked),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = onUpClicked),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarImpl(
    title: String?,
    icon: (@Composable () -> Unit)?,
) {
    TopAppBar(
        title = {
            val t = title ?: return@TopAppBar
            Text(t)
        },
        navigationIcon = icon ?: {},
    )
}

@Composable
private fun NavigationDrawerImpl(
    items: Collection<DrawerMenuItem> = DrawerMenuItem.entries,
    onClicked: (DrawerMenuItem) -> Unit,
) {
    ModalDrawerSheet {
        items.forEach {
            ListItem(
                headlineContent = { Text(it.text()) },
                modifier = Modifier.clickable(onClick = { onClicked(it) }),
            )
        }
    }
}

internal enum class DrawerMenuItem(
    val text: @Composable () -> String,
) {
    SUBSCRIPTION(
        text = { stringResource(R.string.title_subscription) },
    ),
    AUTH_STATUS(
        text = { stringResource(R.string.title_account_setting) },
    ),
    APP_SETTING(
        text = { stringResource(R.string.title_setting) },
    ),
    OSS_LICENSE(
        text = { stringResource(R.string.title_oss_license) },
    ),
}

@LightDarkModePreview
@Composable
private fun MainScreenPreview() {
    AppTheme {
        TopAppBarImpl(
            title = stringResource(id = R.string.title_timetable),
            icon = {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = {}),
                )
            }
        )
    }
}

@LightDarkModePreview
@Composable
private fun NavDrawerPreview() {
    AppTheme {
        NavigationDrawerImpl(onClicked = {})
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @OssLicenseNavigationQualifier private val ossLicensePage: NavActivity,
) : ViewModel() {
    val navigation: Set<NavR> = (MainNavRoute.routes + ossLicensePage).toSet()
    val startDestination = LiveVideoSharedTransitionRoute.TimetableTab.route
    internal fun getDrawerRoute(item: DrawerMenuItem): String {
        return when (item) {
            DrawerMenuItem.SUBSCRIPTION -> MainNavRoute.Subscription.route
            DrawerMenuItem.AUTH_STATUS -> MainNavRoute.Auth.route
            DrawerMenuItem.APP_SETTING -> MainNavRoute.Settings.route
            DrawerMenuItem.OSS_LICENSE -> ossLicensePage.path
        }
    }
}

@Qualifier
annotation class OssLicenseNavigationQualifier
