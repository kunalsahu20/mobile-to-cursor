package com.vexra.app.ui

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Trackpad gesture surface — glass-morphism design.
 *
 * 1 finger  → cursor move / tap = left click / hold = drag
 * 2 fingers → scroll / tap = right click
 * 3 fingers → swipe gestures / tap = Search (Win key)
 * 4 fingers → volume · desktop switch / tap = notifications
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackpadSurface(
    onMove: (dx: Int, dy: Int) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onScroll: (dx: Int, dy: Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
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

    // 2-finger drag state
    var isDragging by remember { mutableIntStateOf(0) } // 0=no, 1=yes
    var dragHoldTime by remember { mutableFloatStateOf(0f) }

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

                            // 2-finger: scroll
                            fingerCount == 2 && pointerCount >= 2 -> {
                                val midX = (event.getX(0) + event.getX(1)) / 2f
                                val midY = (event.getY(0) + event.getY(1)) / 2f
                                val scrollDx = midX - lastScrollX
                                val scrollDy = midY - lastScrollY
                                multiTotalMovement += abs(scrollDx) + abs(scrollDy)

                                // Normal 2-finger scroll
                                if (abs(scrollDx) > 1f || abs(scrollDy) > 1f) {
                                    onScroll(
                                        (scrollDx / 5f).toInt(),
                                        -(scrollDy / 5f).toInt()
                                    )
                                    lastScrollX = midX
                                    lastScrollY = midY
                                }
                            }

                            // 1-finger cursor movement or drag
                            fingerCount == 1 -> {
                                val dx = event.x - lastX
                                val dy = event.y - lastY
                                
                                val holdDuration = event.eventTime - downTime.toLong()
                                if (holdDuration > 400 && isDragging == 0 && totalMovement < 30f) {
                                    isDragging = 1
                                    onDragStart()
                                }
                                
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
                        // If we were dragging on multi-touch, release mouse button
                        if (isDragging == 1) {
                            onDragEnd()
                            isDragging = 0
                        }
                        fingerCount = pointerCount - 1
                        if (fingerCount == 1) {
                            val remaining = if (event.actionIndex == 0) 1 else 0
                            lastX = event.getX(remaining)
                            lastY = event.getY(remaining)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        // Release drag if still active
                        if (isDragging == 1) {
                            onDragEnd()
                            isDragging = 0
                            fingerCount = 0
                            maxFingerCount = 0
                            return@pointerInteropFilter true
                        }

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
        color = Color.Black.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)),
    ) {
        // Subtle inner gradient for glass depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Centered gesture hint (fades on touch)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.SwipeUp,
                contentDescription = "Swipe",
                tint = Color(0xFF94A3B8).copy(alpha = 0.60f),
                modifier = Modifier.height(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Swipe to move\nTwo-finger scroll",
                color = Color(0xFF94A3B8).copy(alpha = 0.60f),
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
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
