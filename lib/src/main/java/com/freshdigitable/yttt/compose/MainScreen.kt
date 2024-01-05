package com.freshdigitable.yttt.compose

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.navigation.composableWith
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.lib.R
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(Unit) {
        logD("MainScreen") { "DisposableEffect: " }
        val listener = Consumer<Intent> {
            val handled = navController.handleDeepLink(it)
            logD("MainScreen") { "handleDeepLink(handled>$handled): ${it.data}" }
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    MainScreen(
        drawerState = drawerState,
        navController = navController,
    )
}

@Composable
private fun MainScreen(
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    navController: NavHostController = rememberNavController(),
) {
    val coroutineScope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerImpl(onClicked = {
                navController.navigate(it)
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
            NavHost(
                modifier = Modifier.padding(padding),
                navController = navController,
                startDestination = MainNavRoute.TimetableTab.route,
            ) {
                composableWith(navController = navController, navRoutes = MainNavRoute.routes)
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
            if (backStack == null || route == MainNavRoute.TimetableTab.route) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = onMenuIconClicked),
                )
            } else {
                Icon(
                    Icons.Filled.ArrowBack,
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
    items: Collection<DrawerMenuItem> = DrawerMenuItem.values().toList(),
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

private enum class DrawerMenuItem(
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
}

private fun NavHostController.navigate(item: DrawerMenuItem) {
    when (item) {
        DrawerMenuItem.SUBSCRIPTION -> navigate(MainNavRoute.Subscription.route)
        DrawerMenuItem.AUTH_STATUS -> navigate(MainNavRoute.Auth.route)
        DrawerMenuItem.APP_SETTING -> navigate(MainNavRoute.Settings.route)
    }
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
