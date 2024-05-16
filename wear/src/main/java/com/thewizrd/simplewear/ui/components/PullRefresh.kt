package com.thewizrd.simplewear.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullRefresh(
    modifier: Modifier = Modifier,
    state: PullRefreshState,
    indicator: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.pullRefresh(state)
    ) {
        content()

        indicator()
    }
}