package com.japanese.ohanashi

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp


enum class Direction() {
    Left,
    Right,
    Up,
    Down
}

class SemicircleShape(val direction: Direction) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(
            path = Path().apply {
                reset()
                var left = 0f
                var right = 0f
                var top = 0f
                var bottom = 0f
                var startAngleDegrees = 0f
                var sweepAngleDegrees = 0f
                when (direction) {
                    Direction.Left -> {
                        left   = 0f
                        right  = size.width * 2f
                        top    = 0f
                        bottom = size.height
                        startAngleDegrees = -90f
                        sweepAngleDegrees = -180f
                    }
                    Direction.Right -> {
                        left   = -size.width
                        right  =  size.width
                        top    =  0f
                        bottom =  size.height
                        startAngleDegrees = -90f
                        sweepAngleDegrees = 180f
                    }
                    Direction.Up -> {
                        left   = 0f
                        right  = size.width
                        top    = 0f
                        bottom = size.height * 2f
                        startAngleDegrees = -180f
                        sweepAngleDegrees =  180f
                    }
                    Direction.Down -> {
                        left   =  0f
                        right  =  size.width
                        top    = -size.height
                        bottom =  size.height
                        startAngleDegrees = 0f
                        sweepAngleDegrees = 180f
                    }
                }

                arcTo(
                    rect = Rect(
                        left = left,
                        right = right,
                        top = top,
                        bottom = bottom,
                    ),
                    startAngleDegrees = startAngleDegrees,
                    sweepAngleDegrees = sweepAngleDegrees,
                    forceMoveTo = false
                )
            }
        )
    }
}

@ExperimentalMaterialApi
@Composable
fun SwipeActionArea(direction: Direction, swipeAreaWidth: Dp, modifier: Modifier, onSwipeAction: () -> Unit) {
    val sizePx = with(LocalDensity.current) { swipeAreaWidth.toPx() }

    val swipeableState = rememberSwipeableState(
        initialValue = 0,
        confirmStateChange = {
            if (it >= 1)  onSwipeAction()
            // we never want to stay in the target state, it's just a trigger
            false
        }
    )
    val alignment: Alignment
    val mod: Modifier
    when (direction) {
        Direction.Right -> {
            alignment = Alignment.CenterStart
            mod = Modifier.fillMaxHeight().width(swipeableState.offset.value.dp)
        }
        Direction.Left -> {
            alignment = Alignment.CenterEnd
            mod = Modifier.fillMaxHeight().width(swipeableState.offset.value.dp)
        }
        Direction.Up -> {
            alignment = Alignment.BottomCenter
            mod = Modifier.fillMaxWidth().height(swipeableState.offset.value.dp)
        }
        else -> {
            alignment = Alignment.TopCenter
            mod = Modifier.fillMaxWidth().height(swipeableState.offset.value.dp)
        }
    }
    Box(
        modifier = modifier
            .swipeable(
                state = swipeableState,
                anchors = mapOf(0f to 0, sizePx to 1),
                orientation = if (direction==Direction.Left || direction == Direction.Right) Orientation.Horizontal else Orientation.Vertical,
                reverseDirection = direction == Direction.Left,
            ),
        contentAlignment = alignment
    ) {
        Box(
            modifier = mod
                .clip(SemicircleShape(direction))
                .background(Color(.5f, .5f, .5f, 0.2f))
        )
    }
}
