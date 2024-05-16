package com.thewizrd.simplewear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx

@Preview
@Composable
fun WearDivider(
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(ctx.dpToPx(2f), ctx.dpToPx(4f)))

        Canvas(
            modifier = Modifier
                .width(48.dp)
                .height(1.dp)
        ) {
            drawLine(
                color = Color.White,
                strokeWidth = ctx.dpToPx(2f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                pathEffect = pathEffect
            )
        }
    }
}