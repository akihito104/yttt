package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.accompanist.themeadapter.material.MdcTheme

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LiveChannelListItemView(iconUrl: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 8.dp)
            .padding(start = 16.dp, end = 24.dp),
    ) {
        if (iconUrl.isEmpty()) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "",
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Top)
            )
        } else {
            GlideImage(
                model = iconUrl,
                contentDescription = "",
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Top)
                    .clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun LiveChannelListItemViewPreviewNight() {
    MdcTheme {
        LiveChannelListItemView("", "title")
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun LiveChannelListItemViewPreview() {
    MdcTheme {
        LiveChannelListItemView("", "title")
    }
}
