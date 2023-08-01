package com.freshdigitable.yttt

import android.content.Intent

interface NewChooseAccountIntentProvider {
    operator fun invoke(): Intent
}
