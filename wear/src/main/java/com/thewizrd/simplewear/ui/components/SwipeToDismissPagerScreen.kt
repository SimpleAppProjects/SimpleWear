package com.thewizrd.simplewear.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.HierarchicalFocusCoordinator
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import com.google.android.horologist.compose.layout.PagerScaffold
import com.google.android.horologist.compose.pager.HorizontalPagerDefaults
import kotlinx.coroutines.coroutineScope

// https://slack-chats.kotlinlang.org/t/16230979/problem-changing-basicswipetodismiss-background-color-gt
@OptIn(ExperimentalFoundationApi::class, ExperimentalWearFoundationApi::class)
@Composable
fun SwipeToDismissPagerScreen(
    modifier: Modifier = Modifier,
    state: PagerState,
    hidePagerIndicator: Boolean = false,
    timeText: (@Composable () -> Unit)? = null,
    pagerKey: ((index: Int) -> Any)? = null,
    content: @Composable (Int) -> Unit
) {
    val screenWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    var allowPaging by remember { mutableStateOf(true) }

    val originalTouchSlop = LocalViewConfiguration.current.touchSlop

    CustomTouchSlopProvider(newTouchSlop = originalTouchSlop * 2) {
        PagerScaffold(
            modifier = Modifier.fillMaxSize(),
            timeText = timeText,
            pagerState = if (hidePagerIndicator) null else state
        ) {
            HorizontalPager(
                modifier = modifier
                    .pointerInput(screenWidth) {
                        coroutineScope {
                            awaitEachGesture {
                                allowPaging = true
                                val firstDown =
                                    awaitFirstDown(false, PointerEventPass.Initial)
                                val xPosition = firstDown.position.x
                                // Define edge zone of 15%
                                allowPaging = xPosition > screenWidth * 0.15f
                            }
                        }
                    }
                    .semantics {
                        horizontalScrollAxisRange = if (allowPaging) {
                            ScrollAxisRange(value = { state.currentPage.toFloat() },
                                maxValue = { 3f })
                        } else {
                            // signals system swipe to dismiss that they can take over
                            ScrollAxisRange(value = { 0f },
                                maxValue = { 0f })
                        }
                    },
                state = state,
                flingBehavior = PagerDefaults.flingBehavior(
                    state,
                    snapAnimationSpec = tween(150, 0),
                ),
                userScrollEnabled = allowPaging,
                key = pagerKey
            ) { page ->
                HierarchicalFocusCoordinator(requiresFocus = { page == state.currentPage }) {
                    content(page)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToDismissPagerScreen(
    modifier: Modifier = Modifier,
    isRoot: Boolean = true,
    swipeToDismissBoxState: SwipeToDismissBoxState = rememberSwipeToDismissBoxState(),
    state: PagerState,
    hidePagerIndicator: Boolean = false,
    timeText: (@Composable () -> Unit)? = null,
    pagerKey: ((index: Int) -> Any)? = null,
    content: @Composable (Int) -> Unit
) {
    if (isRoot) {
        SwipeToDismissPagerScreen(
            modifier,
            state,
            hidePagerIndicator,
            timeText,
            pagerKey,
            content
        )
    } else {
        SwipeToDismissPagerScreen(
            modifier,
            swipeToDismissBoxState,
            state,
            hidePagerIndicator,
            timeText,
            pagerKey,
            content
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalWearFoundationApi::class)
@Composable
private fun SwipeToDismissPagerScreen(
    modifier: Modifier = Modifier,
    swipeToDismissBoxState: SwipeToDismissBoxState = rememberSwipeToDismissBoxState(),
    state: PagerState,
    hidePagerIndicator: Boolean = false,
    timeText: (@Composable () -> Unit)? = null,
    pagerKey: ((index: Int) -> Any)? = null,
    content: @Composable (Int) -> Unit
) {
    PagerScaffold(
        modifier = Modifier
            .fillMaxSize()
            .edgeSwipeToDismiss(swipeToDismissBoxState),
        timeText = timeText,
        pagerState = if (hidePagerIndicator) null else state
    ) {
        HorizontalPager(
            modifier = modifier,
            state = state,
            flingBehavior = HorizontalPagerDefaults.flingParams(state),
            key = pagerKey
        ) { page ->
            HierarchicalFocusCoordinator(requiresFocus = { page == state.currentPage }) {
                content(page)
            }
        }
    }
}

//MARK: - Horologist code

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClippedBox(pagerState: PagerState, content: @Composable () -> Unit) {
    val shape = rememberClipWhenScrolling(pagerState)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .optionalClip(shape),
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberClipWhenScrolling(state: PagerState): State<RoundedCornerShape?> {
    val shape = if (LocalConfiguration.current.isScreenRound) CircleShape else null
    return remember(state) {
        derivedStateOf {
            if (shape != null && state.currentPageOffsetFraction != 0f) {
                shape
            } else {
                null
            }
        }
    }
}

private fun Modifier.optionalClip(shapeState: State<RoundedCornerShape?>): Modifier {
    val shape = shapeState.value

    return if (shape != null) {
        clip(shape)
    } else {
        this
    }
}


// MARK: - Steve Bower code

@Composable
private fun CustomTouchSlopProvider(
    newTouchSlop: Float,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalViewConfiguration provides CustomTouchSlop(
            newTouchSlop,
            LocalViewConfiguration.current
        )
    ) {
        content()
    }
}

private class CustomTouchSlop(
    private val customTouchSlop: Float,
    currentConfiguration: ViewConfiguration
) : ViewConfiguration by currentConfiguration {
    override val touchSlop: Float
        get() = customTouchSlop
}