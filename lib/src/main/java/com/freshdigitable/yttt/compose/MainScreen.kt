package com.freshdigitable.yttt.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.DrawerMenuListItem.Companion.toListItem
import com.freshdigitable.yttt.compose.navigation.NavActivity
import com.freshdigitable.yttt.compose.navigation.NavParam.Companion.route
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.ScreenStateHolder
import com.freshdigitable.yttt.compose.navigation.composableWith
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.lib.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val showMenuBadge = viewModel.showMenuBadge.collectAsState()
    val drawerMenuItems = viewModel.drawerMenuItems.collectAsState()
    MainScreen(
        navController = navController,
        navigation = viewModel.routes,
        startDestination = viewModel.startDestination,
        showMenuBadge = { showMenuBadge.value },
        drawerItems = { drawerMenuItems.value },
        snackbarMessage = viewModel.snackbarMessage,
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
    navigation: Set<NavRoute>,
    startDestination: String,
    showMenuBadge: () -> Boolean,
    drawerItems: () -> List<DrawerMenuListItem>,
    snackbarMessage: Flow<SnackbarAction>,
    onDrawerMenuClick: (DrawerMenuItem) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage.collectLatest { action ->
            val res = snackbarHostState.showSnackbar(action.message)
            if (res == SnackbarResult.ActionPerformed) {
                when (action) {
                    is SnackbarAction.NavigationAction -> action(navController)
                    is SnackbarAction.CustomAction -> action()
                }
            }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerImpl(
                items = drawerItems,
                onClicked = {
                    onDrawerMenuClick(it)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
            )
        },
    ) {
        val backStack = navController.currentBackStackEntryFlow
            .map { it.destination.route == startDestination }
            .collectAsState(initial = true)
        val topAppBarStateHolder = remember {
            val navIconState = NavigationIconStateImpl(
                isRoot = { backStack.value },
                isBadgeShown = showMenuBadge,
                onMenuIconClicked = drawerState::open,
                onUpClicked = navController::navigateUp,
            )
            TopAppBarStateHolder(navIconState)
        }
        Scaffold(
            topBar = { AppTopAppBar(stateHolder = topAppBarStateHolder) },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { padding ->
            SharedTransitionLayout {
                NavHost(
                    modifier = Modifier.padding(padding),
                    navController = navController,
                    startDestination = startDestination,
                ) {
                    composableWith(
                        screenStateHolder = ScreenStateHolder(
                            navController,
                            topAppBarStateHolder,
                            this@SharedTransitionLayout,
                            snackbarHostState = snackbarHostState,
                        ),
                        navRoutes = navigation
                    )
                }
            }
        }
    }
}

@Immutable
private class NavigationIconStateImpl(
    override val isRoot: () -> Boolean,
    override val isBadgeShown: () -> Boolean,
    override val onMenuIconClicked: suspend () -> Unit,
    override val onUpClicked: () -> Unit,
) : NavigationIconState

@Composable
private fun NavigationDrawerImpl(
    items: () -> Collection<DrawerMenuListItem>,
    onClicked: (DrawerMenuItem) -> Unit,
) {
    ModalDrawerSheet {
        items().forEach {
            ListItem(
                headlineContent = {
                    BadgedBox(
                        badge = {
                            if (it.showBadge) {
                                Badge(containerColor = Color.Red)
                            }
                        }
                    ) {
                        Text(it.item.text())
                    }
                },
                modifier = Modifier.clickable(onClick = { onClicked(it.item) }),
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
private fun NavDrawerPreview() {
    AppTheme {
        NavigationDrawerImpl(items = {
            listOf(
                DrawerMenuItem.SUBSCRIPTION.toListItem(),
                DrawerMenuItem.AUTH_STATUS.toListItem(true),
                DrawerMenuItem.APP_SETTING.toListItem(),
                DrawerMenuItem.OSS_LICENSE.toListItem(),
            )
        }, onClicked = {})
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @OssLicenseNavigationQualifier private val ossLicensePage: NavActivity,
    accountRepository: TwitchAccountRepository,
) : ViewModel() {
    val routes: Set<NavRoute> = (MainNavRoute.routes + ossLicensePage).toSet()
    val startDestination = MainNavRoute.startDestination
    internal val drawerMenuItems = combine<DrawerMenuListItem, List<DrawerMenuListItem>>(
        listOf(
            flowOf(DrawerMenuItem.SUBSCRIPTION.toListItem()),
            accountRepository.isTwitchTokenInvalidated.map {
                DrawerMenuItem.AUTH_STATUS.toListItem(it ?: false)
            },
            flowOf(DrawerMenuItem.APP_SETTING.toListItem()),
            flowOf(DrawerMenuItem.OSS_LICENSE.toListItem()),
        )
    ) {
        it.toList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val showMenuBadge = drawerMenuItems.map { i -> i.any { it.showBadge } }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val snackbarMessage = merge(
        accountRepository.isTwitchTokenInvalidated.filter { it ?: false }
            .map {
                SnackbarAction.NavigationAction(
                    SnackbarMessage(
                        message = "Your Twitch login credential has expired.",
                        actionLabel = "account setting",
                        withDismissAction = false,
                        duration = SnackbarDuration.Long,
                    )
                ) {
                    it.navigate(MainNavRoute.Auth.route)
                }
            }
    )

    internal fun getDrawerRoute(item: DrawerMenuItem): String {
        return when (item) {
            DrawerMenuItem.SUBSCRIPTION -> MainNavRoute.Subscription.route
            DrawerMenuItem.AUTH_STATUS -> MainNavRoute.Auth.route
            DrawerMenuItem.APP_SETTING -> MainNavRoute.Settings.route
            DrawerMenuItem.OSS_LICENSE -> ossLicensePage.root
        }
    }
}

sealed class SnackbarAction {
    abstract val message: SnackbarVisuals

    data class NavigationAction(
        override val message: SnackbarMessage,
        val action: (NavHostController) -> Unit,
    ) : SnackbarAction() {
        operator fun invoke(navController: NavHostController) {
            action(navController)
        }
    }

    data class CustomAction(
        override val message: SnackbarMessage,
        val action: () -> Unit,
    ) : SnackbarAction() {
        operator fun invoke() {
            action()
        }
    }
}

internal data class DrawerMenuListItem(
    val item: DrawerMenuItem,
    val showBadge: Boolean = false,
    val badgeContent: Int? = null,
) {
    companion object {
        internal fun DrawerMenuItem.toListItem(
            showBadge: Boolean = false,
            badgeContent: Int? = null,
        ): DrawerMenuListItem = DrawerMenuListItem(this, showBadge, badgeContent)
    }
}

@Qualifier
annotation class OssLicenseNavigationQualifier
