package com.amirhparhizgar.utdiningwidget.pulltoload

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.amirhparhizgar.utdiningwidget.PullToLoadState
import com.amirhparhizgar.utdiningwidget.R
import com.amirhparhizgar.utdiningwidget.TAG

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
    Log.d(TAG, "loading->PullToLoadIndicator: loading=$loading, height = ${state.height.dp}")
    Crossfade(
        targetState = loading,
        animationSpec = tween(durationMillis = CrossfadeDurationMs)
    ) { targetLoading ->
        Box(
            modifier = modifier
                .height(state.height.dp)
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