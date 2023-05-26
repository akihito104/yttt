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
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.R
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
            TopAppBarImpl(
                navController,
                onMenuIconClicked = {
                    coroutineScope.launch {
                        drawerState.open()
                    }
                },
            )
        },
        drawerContent = {
            DrawerContent(
                onSubscriptionMenuClicked = {
                    navController.navigateToSubscriptionList(it)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
                onTwitchOauth = {
                    navController.navigate(MainNavRoute.TwitchLogin.path)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
            )
        },
    ) { padding ->
        MainNavHost(navController, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun TopAppBarImpl(
    navController: NavController,
    onMenuIconClicked: () -> Unit,
) {
    val backStack = navController.currentBackStackEntryAsState().value
    TopAppBar(
        title = { Text(stringResource(id = R.string.app_name)) },
        navigationIcon = {
            if (backStack == null || backStack.destination.route == "ttt") {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = onMenuIconClicked),
                )
            } else {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "",
                    modifier = Modifier.clickable(onClick = {
                        navController.navigateUp()
                    }),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ColumnScope.DrawerContent(
    onSubscriptionMenuClicked: (LivePlatform) -> Unit,
    onTwitchOauth: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = { onSubscriptionMenuClicked(LivePlatform.YOUTUBE) }),
        text = { Text("Subscription") }
    )
    ListItem(
        modifier = Modifier.clickable(onClick = { onSubscriptionMenuClicked(LivePlatform.TWITCH) }),
        text = { Text("Twitch Subscription") }
    )
    ListItem(
        modifier = Modifier.clickable(onClick = onTwitchOauth),
        text = { Text(text = "twitch auth") }
    )
}

@Preview
@Composable
fun MainScreenPreview() {
    MdcTheme {
        MainScreen()
    }
}
