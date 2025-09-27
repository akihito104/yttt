package com.freshdigitable.yttt.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.freshdigitable.yttt.compose.preview.PreviewLightDarkMode
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.di.LivePlatformMap
import com.freshdigitable.yttt.lib.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Qualifier

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel { f: MainViewModel.Factory ->
        f.create(SnackbarMessageBus.create())
    },
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
        snackbarMessageSender = viewModel.sender,
        onDrawerMenuClick = {
            val route = viewModel.getDrawerRoute(it)
            navController.navigate(route)
        },
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScreen(
    navigation: Set<NavRoute>,
    startDestination: String,
    showMenuBadge: () -> Boolean,
    drawerItems: () -> List<DrawerMenuListItem>,
    snackbarMessage: Flow<SnackbarAction>,
    snackbarMessageSender: SnackbarMessageBus.Sender,
    navController: NavHostController = rememberNavController(),
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
                    is SnackbarAction.NopAction -> Unit
                }
            }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerImpl(
                items = drawerItems,
                onClick = {
                    onDrawerMenuClick(it)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
            )
        },
    ) {
        MainScreenContent(
            navController,
            startDestination,
            showMenuBadge,
            drawerState,
            snackbarHostState,
            snackbarMessageSender,
            navigation,
        )
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun MainScreenContent(
    navController: NavHostController,
    startDestination: String,
    showMenuBadge: () -> Boolean,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    snackbarMessageSender: SnackbarMessageBus.Sender,
    navigation: Set<NavRoute>,
) {
    val flow = remember(navController.currentBackStackEntryFlow, startDestination) {
        navController.currentBackStackEntryFlow
            .map { it.destination.route == startDestination }
    }
    val backStack = flow.collectAsState(initial = true)
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
                        snackbarBus = snackbarMessageSender,
                    ),
                    navRoutes = navigation,
                )
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
    onClick: (DrawerMenuItem) -> Unit,
) {
    ModalDrawerSheet {
        items().forEach {
            NavigationDrawerItem(
                label = { Text(it.item.text()) },
                badge = { if (it.showBadge) Badge(containerColor = Color.Red) },
                selected = false,
                onClick = { onClick(it.item) },
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

@PreviewLightDarkMode
@Composable
private fun NavDrawerPreview() {
    AppTheme {
        NavigationDrawerImpl(
            items = {
                listOf(
                    DrawerMenuItem.SUBSCRIPTION.toListItem(),
                    DrawerMenuItem.AUTH_STATUS.toListItem(true),
                    DrawerMenuItem.APP_SETTING.toListItem(),
                    DrawerMenuItem.OSS_LICENSE.toListItem(),
                )
            },
            onClick = {},
        )
    }
}

@HiltViewModel(assistedFactory = MainViewModel.Factory::class)
class MainViewModel @AssistedInject constructor(
    @param:OssLicenseNavigationQualifier private val ossLicensePage: NavActivity,
    accountRepositories: LivePlatformMap<AccountRepository>,
    @Assisted private val messageBus: SnackbarMessageBus,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(messageBus: SnackbarMessageBus): MainViewModel
    }

    val routes: Set<NavRoute> = (MainNavRoute.routes + ossLicensePage).toSet()
    val startDestination = MainNavRoute.startDestination
    private val isTokenInvalid: Flow<Boolean?> =
        combine(accountRepositories.map { it.value.isTokenInvalid }) { i -> i.any { it == true } }
            .onStart { emit(false) }
    internal val drawerMenuItems = combine<DrawerMenuListItem, List<DrawerMenuListItem>>(
        listOf(
            flowOf(DrawerMenuItem.SUBSCRIPTION.toListItem()),
            isTokenInvalid.map { DrawerMenuItem.AUTH_STATUS.toListItem(it == true) },
            flowOf(DrawerMenuItem.APP_SETTING.toListItem()),
            flowOf(DrawerMenuItem.OSS_LICENSE.toListItem()),
        ),
    ) {
        it.toList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val showMenuBadge = drawerMenuItems.map { i -> i.any { it.showBadge } }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val sender get() = messageBus.getSender()
    val snackbarMessage = merge(
        messageBus.messageFlow.map { SnackbarAction.NopAction(it) },
        accountRepositories.map { r ->
            r.value.isTokenInvalid.map { if (it == true) r.key else null }
        }.merge().filterNotNull().map { p ->
            SnackbarAction.NavigationAction(
                SnackbarMessage(
                    message = "Your ${p.name} login credential has expired.",
                    actionLabel = "account setting",
                    withDismissAction = false,
                    duration = SnackbarDuration.Long,
                ),
            ) {
                it.navigate(MainNavRoute.Auth.route)
            }
        },
    )

    internal fun getDrawerRoute(item: DrawerMenuItem): String {
        return when (item) {
            DrawerMenuItem.SUBSCRIPTION -> MainNavRoute.Subscription.route
            DrawerMenuItem.AUTH_STATUS -> MainNavRoute.Auth.route
            DrawerMenuItem.APP_SETTING -> MainNavRoute.Settings.route
            DrawerMenuItem.OSS_LICENSE -> ossLicensePage.root
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageBus.close()
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

    data class NopAction(override val message: SnackbarVisuals) : SnackbarAction()
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
