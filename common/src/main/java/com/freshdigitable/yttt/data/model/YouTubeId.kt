package com.freshdigitable.yttt.data.model

interface YouTubeId : IdBase

object YouTube : LivePlatform {
    override val name: String = "YouTube"
    override val color: Long = BRAND_COLOR

    // https://brand.youtube/#color
    private const val BRAND_COLOR = 0xFFFF0033
}
