package com.amirhparhizgar.utdiningwidget.pulltoload

import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.platform.inspectable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.amirhparhizgar.utdiningwidget.ui.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */
object PullToLoadDefaults {
    /**
     * If the indicator is below this threshold offset when it is released, a load
     * will be triggered.
     */
    val LoadThreshold = 24.dp

    /**
     * The offset at which the indicator should be rendered whilst a refresh is occurring.
     */
    val LoadingOffset = 20.dp
}

private const val DragMultiplier = 0.5f

class PullToLoadState internal constructor(
    private val animationScope: CoroutineScope,
    private val onRefreshState: State<() -> Unit>,
    private val refreshingOffset: Float,
    internal val threshold: Float
) {
    /**
     * A float representing how far the user has pulled as a percentage of the refreshThreshold.
     *
     * If the component has not been pulled at all, progress is zero. If the pull has reached
     * halfway to the threshold, progress is 0.5f. A value greater than 1 indicates that pull has
     * gone beyond the refreshThreshold - e.g. a value of 2f indicates that the user has pulled to
     * two times the refreshThreshold.
     */
    val progress get() = adjustedDistancePulled / threshold

    internal val loading get() = _loading
    val height get() = _height

    private val adjustedDistancePulled by derivedStateOf { distancePulled * DragMultiplier }

    private var _loading by mutableStateOf(false)
    private var _height by mutableStateOf(0f)
    private var distancePulled by mutableStateOf(0f)

    internal fun onPull(pullDelta: Float): Float {
        if (this._loading) return 0f // Already refreshing, do nothing.

        val newOffset = (distancePulled + pullDelta).coerceAtLeast(0f)
        val dragConsumed = newOffset - distancePulled
        distancePulled = newOffset
        _height = calculateIndicatorPosition()
        return dragConsumed
    }

    internal fun onRelease() {
        Log.d(TAG, "PullToLoadState->onRelease loading=$_loading")
        if (!this._loading) {
            if (adjustedDistancePulled > threshold) {
                Log.d(TAG, "PullToLoadState->onRelease: onLoad")
                onRefreshState.value()
            } else {
                Log.d(TAG, "onRelease: animating to 0")
                animateIndicatorHeightTo(0f)
            }
        }
        distancePulled = 0f
    }

    internal fun setLoading(loading: Boolean) {
        if (this._loading != loading || !loading) {
            this._loading = loading
            this.distancePulled = 0f
            animateIndicatorHeightTo(if (loading) refreshingOffset else 0f)
        }
    }

    private fun animateIndicatorHeightTo(height: Float) = animationScope.launch {
        Log.d(TAG, "animateIndicatorHeightTo: init=$_height target=$height")
        animate(initialValue = _height, targetValue = height) { value, _ ->
            _height = value
        }
    }

    private fun calculateIndicatorPosition(): Float = when {
        // If drag hasn't gone past the threshold, the position is the adjustedDistancePulled.
        adjustedDistancePulled <= threshold -> adjustedDistancePulled
        else -> {
            // How far beyond the threshold pull has gone, as a percentage of the threshold.
            val overshootPercent = abs(progress) - 1.0f
            // Limit the overshoot to 200%. Linear between 0 and 200.
            val linearTension = overshootPercent.coerceIn(0f, 2f)
            // Non-linear tension. Increases with linearTension, but at a decreasing rate.
            val tensionPercent = linearTension - linearTension.pow(2) / 4
            // The additional offset beyond the threshold.
            val extraOffset = threshold * tensionPercent
            threshold + extraOffset
        }
    }
}

@Composable
fun rememberPullToLoadState(
    loading: Boolean,
    onLoad: () -> Unit,
    loadThreshold: Dp = PullToLoadDefaults.LoadThreshold,
    loadingOffset: Dp = PullToLoadDefaults.LoadingOffset,
): PullToLoadState {
    require(loadThreshold > 0.dp) { "The load trigger must be greater than zero!" }

    val scope = rememberCoroutineScope()
    val onRefreshState = rememberUpdatedState(onLoad)
    val thresholdPx: Float
    val refreshingOffsetPx: Float

    with(LocalDensity.current) {
        thresholdPx = loadThreshold.toPx()
        refreshingOffsetPx = loadingOffset.toPx()
    }

    // refreshThreshold and refreshingOffset should not be changed after instantiation, so any
    // changes to these values are ignored.
    val state = remember(scope) {
        PullToLoadState(scope, onRefreshState, refreshingOffsetPx, thresholdPx)
    }

    SideEffect {
        state.setLoading(loading)
    }

    return state
}

fun Modifier.pullToLoad(
    state: PullToLoadState,
    enabled: Boolean = true
) = inspectable(inspectorInfo = debugInspectorInfo {
    name = "pullToLoad"
    properties["state"] = state
    properties["enabled"] = enabled
}) {
    Modifier.pullToLoad(state::onPull, { state.onRelease() }, enabled)
}


fun Modifier.pullToLoad(
    onPull: (pullDelta: Float) -> Float,
    onRelease: suspend (flingVelocity: Float) -> Unit,
    enabled: Boolean = true
) = inspectable(inspectorInfo = debugInspectorInfo {
    name = "pullToLoad"
    properties["onPull"] = onPull
    properties["onRelease"] = onRelease
    properties["enabled"] = enabled
}) {
    Modifier.nestedScroll(PullToLoadNestedScrollConnection(onPull, onRelease, enabled))
}

private class PullToLoadNestedScrollConnection(
    private val onPull: (pullDelta: Float) -> Float,
    private val onRelease: suspend (flingVelocity: Float) -> Unit,
    private val enabled: Boolean
) : NestedScrollConnection {

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource
    ): Offset = when {
        !enabled -> Offset.Zero
        source == NestedScrollSource.Drag && available.y < 0 -> Offset(
            0f,
            onPull(available.y)
        ) // Swiping up
        else -> Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = when {
        !enabled -> Offset.Zero
        source == NestedScrollSource.Drag && available.y > 0 -> Offset(
            0f,
            onPull(available.y)
        ) // Pulling down
        else -> Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        onRelease(available.y)
        return Velocity.Zero
    }
}

