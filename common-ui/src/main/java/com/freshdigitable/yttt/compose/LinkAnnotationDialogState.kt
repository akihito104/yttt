package com.freshdigitable.yttt.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freshdigitable.yttt.data.model.LinkAnnotationRange

class LinkAnnotationDialogState {
    var currentDialog by mutableStateOf<LinkAnnotationRange?>(null)
        private set

    fun showDialog(dialog: LinkAnnotationRange) {
        currentDialog = dialog
    }

    fun dismiss() {
        currentDialog = null
    }
}
