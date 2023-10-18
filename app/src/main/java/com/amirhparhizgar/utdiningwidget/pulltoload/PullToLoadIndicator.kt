package com.amirhparhizgar.utdiningwidget.pulltoload

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amirhparhizgar.utdiningwidget.R

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */

@Composable
fun PullToLoadIndicator(
    loading: Boolean,
    state: PullToLoadState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor)
) {
    Crossfade(
        targetState = loading,
        animationSpec = tween(durationMillis = CrossfadeDurationMs), label = ""
    ) { targetLoading ->
        Box(
            modifier = modifier
                .height { state.height.dp }
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (targetLoading) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = StrokeWidth,
                    modifier = Modifier
                        .fillMaxHeight()
                        .wrapContentWidth(),
                )
            } else {
                Icon(
                    painterResource(id = R.drawable.ic_baseline_expand_more_24),
                    contentDescription = null,
                    Modifier
                        .fillMaxHeight()
                        .wrapContentWidth()
                )
            }
        }
    }

}

private const val CrossfadeDurationMs = 100
private val StrokeWidth = 2.5.dp

fun Modifier.height(height: ()->Dp):Modifier = layout { measurable, constraints ->
    val h = height().toPx().toInt()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = h,
            maxHeight = h
        )
    )
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}