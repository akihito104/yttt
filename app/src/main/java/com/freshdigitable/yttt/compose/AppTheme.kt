package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import com.google.accompanist.themeadapter.material3.Mdc3Theme

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    Mdc3Theme(content = content)
}
