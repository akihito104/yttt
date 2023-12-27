package com.freshdigitable.yttt.compose.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class LightModePreview

@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class DarkModePreview

@LightModePreview
@DarkModePreview
annotation class LightDarkModePreview
