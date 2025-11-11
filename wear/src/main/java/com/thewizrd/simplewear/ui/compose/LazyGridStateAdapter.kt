package com.thewizrd.simplewear.ui.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.wear.compose.foundation.ScrollInfoProvider
import com.thewizrd.shared_resources.utils.Logger

internal class LazyGridScrollInfoProvider(val state: LazyGridState) : ScrollInfoProvider {
    override val isScrollAwayValid: Boolean
        get() = state.layoutInfo.totalItemsCount > 0
    override val isScrollable: Boolean
        get() = state.layoutInfo.totalItemsCount > 0
    override val isScrollInProgress: Boolean
        get() = state.isScrollInProgress
    override val anchorItemOffset: Float
        get() =
            state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                if (it.index != 0) {
                    return@let Float.NaN
                }
                -it.offset.toOffset(state.layoutInfo.orientation).toFloat()
            } ?: Float.NaN
    override val lastItemOffset: Float
        get() {
            val layoutInfo = state.layoutInfo
            val lazyColumnHeightPx = layoutInfo.viewportSize.height
            val reverseLayout = state.layoutInfo.reverseLayout
            return if (reverseLayout) {
                layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                    if (it.index != 0) {
                        return@let 0f
                    }
                    val bottomEdge =
                        -it.offset.toOffset(state.layoutInfo.orientation) + lazyColumnHeightPx + layoutInfo.viewportStartOffset
                    (lazyColumnHeightPx - bottomEdge).toFloat().coerceAtLeast(0f)
                } ?: 0f
            } else {
                layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                    if (it.index != layoutInfo.totalItemsCount - 1) {
                        return@let 0f
                    }
                    val bottomEdge =
                        it.offset.toOffset(state.layoutInfo.orientation) + it.size.toSize(state.layoutInfo.orientation) - layoutInfo.viewportStartOffset
                    (lazyColumnHeightPx - bottomEdge).toFloat().coerceAtLeast(0f)
                } ?: 0f
            }
        }
}

@Composable
fun rememberLazyGridScrollState(lazyGridState: LazyGridState): ScrollState {
    val scrollState = rememberScrollState()

    LaunchedEffect(lazyGridState) {
        snapshotFlow { lazyGridState.layoutInfo }
            .collect { layoutInfo ->
                val positionFraction = lazyGridState.positionFraction

                val viewportSize = if (layoutInfo.orientation == Orientation.Vertical) {
                    layoutInfo.viewportSize.height
                } else {
                    layoutInfo.viewportSize.width
                }

                val sizeFraction = lazyGridState.sizeFraction(viewportSize.toFloat())

                @Suppress("UNCHECKED_CAST")
                runCatching {
                    ScrollState::class.java.getDeclaredField("_maxValueState").run {
                        isAccessible = true
                        (get(scrollState) as MutableState<Int>).run {
                            value = (100 / sizeFraction).toInt()
                        }
                    }

                    ScrollState::class.java.getDeclaredField("value\$delegate").run {
                        isAccessible = true
                        (get(scrollState) as MutableState<Int>).run {
                            value = (positionFraction * 100f / sizeFraction).toInt()
                        }
                    }

                    ScrollState::class.java.getDeclaredField("viewportSize\$delegate").run {
                        isAccessible = true
                        (get(scrollState) as MutableState<Int>).run {
                            value = (viewportSize * sizeFraction).toInt()
                        }
                    }
                }.onFailure {
                    Logger.debug("LazyGridScrollState", it)
                }

                scrollState.scrollTo(scrollState.value)
            }
    }

    return scrollState
}

internal fun IntOffset.toOffset(orientation: Orientation): Int {
    return if (orientation == Orientation.Vertical) {
        this.y
    } else {
        this.x
    }
}

internal fun IntSize.toSize(orientation: Orientation): Int {
    return if (orientation == Orientation.Vertical) {
        this.height
    } else {
        this.width
    }
}

internal val LazyGridState.positionFraction: Float
    get() {
        return if (layoutInfo.visibleItemsInfo.isEmpty()) {
            0.0f
        } else {
            val decimalFirstItemIndex = decimalFirstItemIndex()
            val decimalLastItemIndex = decimalLastItemIndex()

            val decimalLastItemIndexDistanceFromEnd = layoutInfo.totalItemsCount -
                    decimalLastItemIndex

            if (decimalFirstItemIndex + decimalLastItemIndexDistanceFromEnd == 0.0f) {
                0.0f
            } else {
                decimalFirstItemIndex /
                        (decimalFirstItemIndex + decimalLastItemIndexDistanceFromEnd)
            }
        }
    }

internal fun LazyGridState.decimalLastItemIndex(): Float {
    if (layoutInfo.visibleItemsInfo.isEmpty()) return 0f
    val lastItem = layoutInfo.visibleItemsInfo.last()
    // Coerce item sizes to at least 1 to avoid divide by zero for zero height items
    val lastItemVisibleSize =
        (layoutInfo.viewportEndOffset - lastItem.offset.toOffset(layoutInfo.orientation))
            .coerceAtMost(lastItem.size.toSize(layoutInfo.orientation)).coerceAtLeast(1)
    return lastItem.index.toFloat() +
            lastItemVisibleSize.toFloat() / lastItem.size.toSize(layoutInfo.orientation)
        .coerceAtLeast(1).toFloat()
}

internal fun LazyGridState.decimalFirstItemIndex(): Float {
    if (layoutInfo.visibleItemsInfo.isEmpty()) return 0f
    val firstItem = layoutInfo.visibleItemsInfo.first()
    val firstItemOffset =
        firstItem.offset.toOffset(layoutInfo.orientation) - layoutInfo.viewportStartOffset
    // Coerce item size to at least 1 to avoid divide by zero for zero height items
    return firstItem.index.toFloat() -
            firstItemOffset.coerceAtMost(0).toFloat() /
            firstItem.size.toSize(layoutInfo.orientation).coerceAtLeast(1).toFloat()
}

internal fun LazyGridState.sizeFraction(scrollableContainerSizePx: Float) =
    if (layoutInfo.totalItemsCount == 0) {
        1.0f
    } else {
        val decimalFirstItemIndex = decimalFirstItemIndex()
        val decimalLastItemIndex = decimalLastItemIndex()

        (decimalLastItemIndex - decimalFirstItemIndex) /
                layoutInfo.totalItemsCount.toFloat()
    }