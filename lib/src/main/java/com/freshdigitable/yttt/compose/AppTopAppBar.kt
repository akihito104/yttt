package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.freshdigitable.yttt.compose.preview.PreviewLightDarkMode
import com.freshdigitable.yttt.lib.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopAppBar(
    stateHolder: TopAppBarStateHolder,
) {
    TopAppBar(
        title = {
            val t = stateHolder.title ?: return@TopAppBar
            Text(t)
        },
        navigationIcon = stateHolder.navIconState?.let {
            {
                val coroutineScope = rememberCoroutineScope()
                NavigationIcon(
                    isRootProvider = it.isRoot,
                    showMenuBadge = it.isBadgeShown,
                    onMenuIconClicked = {
                        coroutineScope.launch {
                            it.onMenuIconClicked()
                        }
                    },
                    onUpClicked = it.onUpClicked,
                )
            }
        } ?: {},
        actions = {
            if (stateHolder.menuItems.isNotEmpty()) {
                TopAppBarActionMenu { stateHolder.menuItems }
            }
        },
    )
}

@Composable
internal fun RowScope.TopAppBarActionMenu(
    menuItemProvider: () -> List<TopAppBarMenuItem>,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val menuItems = menuItemProvider()
    val onAppBar = menuItems.filterIsInstance<OnAppBar>()
    if (onAppBar.isNotEmpty()) {
        val coroutineScope = rememberCoroutineScope()
        onAppBar.forEach {
            IconButton(
                enabled = it.enabled(),
                onClick = { coroutineScope.launch { it.consumeMenuItem() } },
            ) {
                Icon(imageVector = it.icon, contentDescription = it.text)
            }
        }
    }
    val inOthers = menuItems.filterIsInstance<InOthers>()
    if (inOthers.isNotEmpty()) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            val coroutineScope = rememberCoroutineScope()
            inOthers.forEach {
                DropdownMenuItem(
                    text = { Text(text = it.text) },
                    onClick = {
                        coroutineScope.launch {
                            it.consumeMenuItem()
                        }
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    isRootProvider: () -> Boolean,
    showMenuBadge: () -> Boolean,
    onMenuIconClicked: () -> Unit,
    onUpClicked: () -> Unit,
) {
    if (isRootProvider()) {
        HamburgerMenuIcon(showMenuBadge, onMenuIconClicked)
    } else {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "",
            modifier = Modifier.clickable(onClick = onUpClicked),
        )
    }
}

@Composable
private fun HamburgerMenuIcon(
    showMenuBadge: () -> Boolean,
    onMenuIconClicked: () -> Unit,
) {
    BadgedBox(
        badge = {
            if (showMenuBadge()) {
                Badge(containerColor = Color.Red)
            }
        },
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = "",
            modifier = Modifier.clickable(onClick = onMenuIconClicked),
        )
    }
}

interface NavigationIconState {
    val isRoot: () -> Boolean
    val isBadgeShown: () -> Boolean
    val onMenuIconClicked: suspend () -> Unit
    val onUpClicked: () -> Unit
}

class TopAppBarStateHolder(
    val navIconState: NavigationIconState? = null,
) {
    var title: String? by mutableStateOf(null)
        private set
    var menuItems: List<TopAppBarMenuItem> by mutableStateOf(emptyList())
        private set

    fun update(title: String?, menuItems: List<TopAppBarMenuItem> = emptyList()) {
        this.title = title
        this.menuItems = menuItems
    }

    fun updateMenuItems(menuItems: List<TopAppBarMenuItem>) {
        this.menuItems = menuItems
    }
}

sealed interface TopAppBarMenuItem {
    val text: String
    suspend fun consumeMenuItem()

    companion object {
        fun inOthers(text: String, consume: suspend () -> Unit): TopAppBarMenuItem =
            InOthers(text, consume)

        fun onAppBar(
            text: String,
            icon: ImageVector,
            enabled: () -> Boolean = { true },
            consume: suspend () -> Unit,
        ): TopAppBarMenuItem = OnAppBar(text, icon, enabled, consume)
    }
}

private class InOthers(
    override val text: String,
    private val consume: suspend () -> Unit,
) : TopAppBarMenuItem {
    override suspend fun consumeMenuItem() = consume()
}

private class OnAppBar(
    override val text: String,
    val icon: ImageVector,
    val enabled: () -> Boolean,
    private val consume: suspend () -> Unit,
) : TopAppBarMenuItem {
    override suspend fun consumeMenuItem() = consume()
}

@PreviewLightDarkMode
@Composable
private fun AppTopAppBarPreview() {
    AppTheme {
        val title = stringResource(id = R.string.title_timetable)
        AppTopAppBar(
            stateHolder = TopAppBarStateHolder(
                object : NavigationIconState {
                    override val isRoot: () -> Boolean = { true }
                    override val isBadgeShown: () -> Boolean = { false }
                    override val onMenuIconClicked: suspend () -> Unit = {}
                    override val onUpClicked: () -> Unit = {}
                },
            ).apply {
                update(
                    title = title,
                    menuItems = listOf(
                        TopAppBarMenuItem.onAppBar("menu1", Icons.Default.Refresh) { },
                        TopAppBarMenuItem.onAppBar("menu2", Icons.Default.Add, { false }) { },
                        TopAppBarMenuItem.inOthers("menu1") { },
                        TopAppBarMenuItem.inOthers("menu2") { },
                        TopAppBarMenuItem.inOthers("menu3") { },
                    ),
                )
            },
        )
    }
}

@PreviewLightDarkMode
@Composable
private fun HamburgerMenuIconPreview() {
    AppTheme {
        AppTopAppBar(
            stateHolder = TopAppBarStateHolder(
                object : NavigationIconState {
                    override val isRoot: () -> Boolean = { true }
                    override val isBadgeShown: () -> Boolean = { true }
                    override val onMenuIconClicked: suspend () -> Unit = {}
                    override val onUpClicked: () -> Unit = {}
                },
            ).apply {
                update(title = "Title")
            },
        )
    }
}
