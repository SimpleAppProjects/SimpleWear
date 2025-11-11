package com.thewizrd.simplewear.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

enum class ReorderHapticFeedbackType {
    START,
    MOVE,
    END,
}

open class ReorderHapticFeedback {
    open fun performHapticFeedback(type: ReorderHapticFeedbackType) {
        // no-op
    }
}

@Composable
fun rememberReorderHapticFeedback(): ReorderHapticFeedback {
    val view = LocalView.current

    val reorderHapticFeedback = remember {
        object : ReorderHapticFeedback() {
            override fun performHapticFeedback(type: ReorderHapticFeedbackType) {
                when (type) {
                    ReorderHapticFeedbackType.START ->
                        ViewCompat.performHapticFeedback(
                            view,
                            HapticFeedbackConstantsCompat.DRAG_START
                        )

                    ReorderHapticFeedbackType.MOVE ->
                        ViewCompat.performHapticFeedback(
                            view,
                            HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK
                        )

                    ReorderHapticFeedbackType.END ->
                        ViewCompat.performHapticFeedback(
                            view,
                            HapticFeedbackConstantsCompat.GESTURE_END
                        )
                }
            }
        }
    }

    return reorderHapticFeedback
}