package com.mobiletocursor.ui

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobiletocursor.ui.theme.VexraBorder
import com.mobiletocursor.ui.theme.VexraCard
import com.mobiletocursor.ui.theme.VexraTextDim
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Trackpad gesture surface — glass-morphism design.
 *
 * 1 finger  → cursor move / tap = left click / long press = right click
 * 2 fingers → scroll / pinch = zoom (Ctrl+Scroll) / tap = right click
 * 3 fingers → swipe gestures / tap = Search (Win key)
 * 4 fingers → swipe up/down = volume / swipe L/R = virtual desktop / tap = notifications
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackpadSurface(
    onMove: (dx: Int, dy: Int) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onScroll: (dx: Int, dy: Int) -> Unit,
    onPinchZoom: (zoomIn: Boolean) -> Unit,
    onTwoFingerTap: () -> Unit,
    onThreeFingerSwipe: (direction: String) -> Unit,
    onThreeFingerTap: () -> Unit,
    onFourFingerSwipe: (direction: String) -> Unit,
    onFourFingerTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 1-finger state
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var downTime by remember { mutableFloatStateOf(0f) }
    var totalMovement by remember { mutableFloatStateOf(0f) }

    // Multi-touch state
    var maxFingerCount by remember { mutableIntStateOf(0) }
    var fingerCount by remember { mutableIntStateOf(0) }

    // 2-finger scroll tracking
    var lastScrollX by remember { mutableFloatStateOf(0f) }
    var lastScrollY by remember { mutableFloatStateOf(0f) }

    // 2-finger pinch tracking
    var lastPinchDist by remember { mutableFloatStateOf(0f) }
    var pinchAccumulator by remember { mutableFloatStateOf(0f) }

    // Multi-finger total movement (to distinguish taps from swipes)
    var multiTotalMovement by remember { mutableFloatStateOf(0f) }

    // 3/4-finger gesture: track total delta from start
    var multiStartX by remember { mutableFloatStateOf(0f) }
    var multiStartY by remember { mutableFloatStateOf(0f) }
    var multiGestureFired by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .pointerInteropFilter { event ->
                val action = event.actionMasked
                val pointerCount = event.pointerCount

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                        downTime = event.eventTime.toFloat()
                        totalMovement = 0f
                        multiTotalMovement = 0f
                        fingerCount = 1
                        maxFingerCount = 1
                        multiGestureFired = 0
                        pinchAccumulator = 0f
                        true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        fingerCount = pointerCount
                        if (pointerCount > maxFingerCount) {
                            maxFingerCount = pointerCount
                        }

                        when (pointerCount) {
                            2 -> {
                                lastScrollX = (event.getX(0) + event.getX(1)) / 2f
                                lastScrollY = (event.getY(0) + event.getY(1)) / 2f
                                lastPinchDist = fingerDistance(event)
                                pinchAccumulator = 0f
                            }
                            3, 4 -> {
                                multiStartX = avgX(event)
                                multiStartY = avgY(event)
                                multiGestureFired = 0
                            }
                        }
                        multiTotalMovement = 0f
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        when {
                            // 4-finger gesture
                            fingerCount >= 4 && pointerCount >= 4 -> {
                                val currentX = avgX(event)
                                val currentY = avgY(event)
                                val deltaX = currentX - multiStartX
                                val deltaY = currentY - multiStartY
                                multiTotalMovement += abs(deltaX) + abs(deltaY)

                                val stepsY = (deltaY / 80f).toInt()
                                if (stepsY != multiGestureFired) {
                                    val direction = if (stepsY > multiGestureFired) "down" else "up"
                                    onFourFingerSwipe(direction)
                                    multiGestureFired = stepsY
                                }

                                if (multiGestureFired == 0 && abs(deltaX) > 100f && abs(deltaX) > abs(deltaY)) {
                                    val dir = if (deltaX > 0) "right" else "left"
                                    onFourFingerSwipe(dir)
                                    multiGestureFired = 1
                                }
                            }

                            // 3-finger gesture
                            fingerCount == 3 && pointerCount >= 3 -> {
                                val currentX = avgX(event)
                                val currentY = avgY(event)
                                val deltaX = currentX - multiStartX
                                val deltaY = currentY - multiStartY
                                multiTotalMovement += abs(deltaX) + abs(deltaY)

                                if (multiGestureFired == 0) {
                                    if (abs(deltaX) > 100f && abs(deltaX) > abs(deltaY)) {
                                        val dir = if (deltaX > 0) "right" else "left"
                                        onThreeFingerSwipe(dir)
                                        multiGestureFired = 1
                                    } else if (abs(deltaY) > 100f && abs(deltaY) > abs(deltaX)) {
                                        val dir = if (deltaY > 0) "down" else "up"
                                        onThreeFingerSwipe(dir)
                                        multiGestureFired = 1
                                    }
                                }
                            }

                            // 2-finger: scroll + pinch zoom
                            fingerCount == 2 && pointerCount >= 2 -> {
                                val midX = (event.getX(0) + event.getX(1)) / 2f
                                val midY = (event.getY(0) + event.getY(1)) / 2f
                                val scrollDx = midX - lastScrollX
                                val scrollDy = midY - lastScrollY
                                multiTotalMovement += abs(scrollDx) + abs(scrollDy)

                                if (abs(scrollDx) > 1f || abs(scrollDy) > 1f) {
                                    onScroll(
                                        (scrollDx / 5f).toInt(),
                                        -(scrollDy / 5f).toInt()
                                    )
                                    lastScrollX = midX
                                    lastScrollY = midY
                                }

                                val currentDist = fingerDistance(event)
                                val pinchDelta = currentDist - lastPinchDist
                                pinchAccumulator += pinchDelta
                                lastPinchDist = currentDist

                                val pinchSteps = (pinchAccumulator / 60f).toInt()
                                if (pinchSteps != 0) {
                                    repeat(abs(pinchSteps)) {
                                        onPinchZoom(pinchSteps > 0)
                                    }
                                    pinchAccumulator -= pinchSteps * 60f
                                }
                            }

                            // 1-finger cursor movement
                            fingerCount == 1 -> {
                                val dx = event.x - lastX
                                val dy = event.y - lastY
                                totalMovement += abs(dx) + abs(dy)

                                if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                                    onMove(dx.toInt(), dy.toInt())
                                    lastX = event.x
                                    lastY = event.y
                                }
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        fingerCount = pointerCount - 1
                        if (fingerCount == 1) {
                            val remaining = if (event.actionIndex == 0) 1 else 0
                            lastX = event.getX(remaining)
                            lastY = event.getY(remaining)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val duration = event.eventTime - downTime.toLong()
                        val isTap = duration < 300 && multiGestureFired == 0

                        when {
                            maxFingerCount == 4 && isTap && multiTotalMovement < 50f -> onFourFingerTap()
                            maxFingerCount == 3 && isTap && multiTotalMovement < 50f -> onThreeFingerTap()
                            maxFingerCount == 2 && isTap && multiTotalMovement < 30f -> onTwoFingerTap()
                            maxFingerCount == 1 && totalMovement < 20f -> {
                                if (duration < 300) onTap() else onLongPress()
                            }
                        }
                        fingerCount = 0
                        maxFingerCount = 0
                        true
                    }

                    else -> false
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = VexraCard,
        border = BorderStroke(1.dp, VexraBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "1 finger → cursor · tap → click\n" +
                    "2 fingers → scroll · pinch → zoom\n" +
                    "3 fingers → swipe gestures\n" +
                    "4 fingers → volume · desktop switch",
                color = VexraTextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Calculate distance between first two pointers (for pinch). */
private fun fingerDistance(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 0f
    val dx = event.getX(0) - event.getX(1)
    val dy = event.getY(0) - event.getY(1)
    return sqrt(dx * dx + dy * dy)
}

/** Calculate average Y across all pointers. */
private fun avgY(event: MotionEvent): Float {
    var sum = 0f
    for (i in 0 until event.pointerCount) sum += event.getY(i)
    return sum / event.pointerCount
}

/** Calculate average X across all pointers. */
private fun avgX(event: MotionEvent): Float {
    var sum = 0f
    for (i in 0 until event.pointerCount) sum += event.getX(i)
    return sum / event.pointerCount
}
