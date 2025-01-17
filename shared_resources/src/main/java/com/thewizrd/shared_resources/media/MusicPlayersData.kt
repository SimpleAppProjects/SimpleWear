package com.thewizrd.shared_resources.media

import com.thewizrd.shared_resources.data.AppItemData

data class MusicPlayersData(
    val musicPlayers: Set<AppItemData> = emptySet(),
    val activePlayerKey: String? = null
)