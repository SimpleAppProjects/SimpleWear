package com.thewizrd.simplewear.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerScope
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.PageIndicatorDefaults

@Composable
fun HorizontalPagerScreen(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    hidePagerIndicator: Boolean = false,
    pagerKey: ((index: Int) -> Any)? = null,
    userScrollEnabled: Boolean = true,
    pagerIndicatorBackgroundColor: Color = Color.Unspecified,
    content: @Composable PagerScope.(page: Int) -> Unit,
) {
    HorizontalPagerScaffold(
        modifier = modifier,
        pagerState = pagerState,
        pageIndicator = if (hidePagerIndicator) {
            null
        } else {
            {
                HorizontalPageIndicator(
                    pagerState = pagerState,
                    backgroundColor = if (pagerIndicatorBackgroundColor.isUnspecified) {
                        PageIndicatorDefaults.backgroundColor
                    } else {
                        pagerIndicatorBackgroundColor
                    }
                )
            }
        }
    ) {
        HorizontalPager(
            state = pagerState,
            key = pagerKey,
            userScrollEnabled = userScrollEnabled,
            content = content
        )
    }
}