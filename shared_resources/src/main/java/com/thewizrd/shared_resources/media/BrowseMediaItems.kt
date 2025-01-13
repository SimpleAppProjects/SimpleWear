package com.thewizrd.shared_resources.media

data class BrowseMediaItems(
    val isRoot: Boolean = true,
    val mediaItems: List<MediaItem> = emptyList()
)