package com.thewizrd.shared_resources.tasks

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun CoroutineScope.delayLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    timeMillis: Long,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return this.launch(context, start) {
        runCatching {
            delay(timeMillis)
        }

        if (isActive) {
            block.invoke(this)
        }
    }
}