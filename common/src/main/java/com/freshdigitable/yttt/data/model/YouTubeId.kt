package com.freshdigitable.yttt.data.model

interface YouTubeId : IdBase

object YouTube : LivePlatform {
    override val name: String = "YouTube"
    override val color: Long = 0xFFFF0000
}
