package com.thewizrd.simplewear.ui.components

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Based on [androidx.wear.compose.material3.DefaultTimeSource]
 */
class ElapsedTimeSource(private val startTimeMillis: Long) : TimeSource {
    @Composable
    override fun currentTime(): String =
        elapsedTimeDuration({ System.currentTimeMillis() }, startTimeMillis).value
}

@Composable
private fun elapsedTimeDuration(time: () -> Long, startTimeMillis: Long): State<String> {
    val composableScope = rememberCoroutineScope()
    var currentTime by remember { mutableLongStateOf(time()) }

    val timeText = remember {
        derivedStateOf { formatDuration(currentTime - startTimeMillis) }
    }

    val context = LocalContext.current
    val updatedTimeLambda by rememberUpdatedState(time)

    DisposableEffect(context, updatedTimeLambda) {
        currentTime = updatedTimeLambda()

        val timerJob = composableScope.launch {
            while (isActive) {
                currentTime = updatedTimeLambda()

                val nowMillis = currentTime
                var delayMillis = 1000 - (abs(nowMillis - startTimeMillis) % 1000)

                delayMillis++
                delay(delayMillis)
            }
        }

        onDispose {
            timerJob.cancel()
        }
    }
    return timeText
}

private fun formatDuration(elapsedMillis: Long): String {
    return DateUtils.formatElapsedTime(
        TimeUnit.MILLISECONDS.toSeconds(elapsedMillis)
    )
}
