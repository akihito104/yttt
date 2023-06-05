package com.freshdigitable.yttt.compose

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.util.Consumer
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.R
import com.freshdigitable.yttt.compose.navigation.composableWith
import com.freshdigitable.yttt.data.model.LivePlatform
import com.google.accompanist.themeadapter.material.MdcTheme
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scaffoldState = rememberScaffoldState(drawerState = drawerState)
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(Unit) {
        val listener = Consumer<Intent> { navController.handleDeepLink(it) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    Scaffold(
        scaffoldState = scaffoldState,
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
        drawerContent = {
            DrawerContent(
                items = DrawerMenuItem.values().toList(),
                onClicked = {
                    when (it) {
                        DrawerMenuItem.SUBSCRIPTION_YOUTUBE ->
                            navController.navigateToSubscriptionList(LivePlatform.YOUTUBE)

                        DrawerMenuItem.SUBSCRIPTION_TWITCH ->
                            navController.navigateToSubscriptionList(LivePlatform.TWITCH)

                        DrawerMenuItem.AUTH_TWITCH -> navController.navigateToTwitchLogin()
                    }
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            modifier = Modifier.padding(padding),
            navController = navController,
            startDestination = MainNavRoute.startDestination.route,
        ) {
            composableWith(navController = navController, navRoutes = MainNavRoute.routes)
        }
    }
}

@Composable
private fun TopAppBarImpl(
    currentBackStackEntryProvider: () -> NavBackStackEntry?,
    onMenuIconClicked: () -> Unit,
    onUpClicked: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(id = R.string.app_name)) },
        navigationIcon = {
            val backStack = currentBackStackEntryProvider()
            val route = backStack?.destination?.route
            if (backStack == null || route == MainNavRoute.TimetableTab.route) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = onMenuIconClicked),
                )
            } else if (route == MainNavRoute.Auth.route) {
                // nop
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ColumnScope.DrawerContent(
    items: Collection<DrawerMenuItem>,
    onClicked: (DrawerMenuItem) -> Unit,
) {
    items.forEach {
        ListItem(
            text = { Text(it.text()) },
            modifier = Modifier.clickable(onClick = { onClicked(it) }),
        )
    }
}

private enum class DrawerMenuItem(
    val text: @Composable () -> String,
) {
    SUBSCRIPTION_YOUTUBE(
        text = { "YouTube Subscriptions" },
    ),
    SUBSCRIPTION_TWITCH(
        text = { "Twitch Followings" },
    ),
    AUTH_TWITCH(
        text = { "Twitch auth" },
    ),
}

@Preview
@Composable
fun MainScreenPreview() {
    MdcTheme {
        MainScreen()
    }
}
