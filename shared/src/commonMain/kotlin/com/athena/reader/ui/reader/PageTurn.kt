package com.athena.reader.ui.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.athena.reader.ui.theme.Fiber
import com.athena.reader.ui.theme.Ivory
import com.athena.reader.ui.theme.Parchment
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

internal enum class PageTurnCommand { Next, Previous }

/**
 * A sheet being leafed: the next page lies still, the current page is clipped
 * at a travelling fold, and a paper curl sits on the fold. The fold is painted
 * in the draw phase so the text is not recomposed while it turns.
 */
@Composable
internal fun PageTurnSurface(
    pageIndex: Int,
    pageCount: Int,
    command: PageTurnCommand?,
    onCommandConsumed: () -> Unit,
    onPageChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    page: @Composable BoxScope.(Int) -> Unit,
) {
    val turn = remember { mutableFloatStateOf(0f) }
    val destIndex = remember { mutableIntStateOf(pageIndex) }
    val sheetLive = remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val pageIndexState = rememberUpdatedState(pageIndex)
    val pageCountState = rememberUpdatedState(pageCount)
    val onPageChangeState = rememberUpdatedState(onPageChange)
    val onConsumedState = rememberUpdatedState(onCommandConsumed)
    val commandState = rememberUpdatedState(command)

    LaunchedEffect(Unit) {
        snapshotFlow { commandState.value }
            .filterNotNull()
            .collect { request ->
                onConsumedState.value()
                if (sheetLive.value) return@collect
                val index = pageIndexState.value
                val count = pageCountState.value
                val delta = when (request) {
                    PageTurnCommand.Next -> if (index < count - 1) 1 else 0
                    PageTurnCommand.Previous -> if (index > 0) -1 else 0
                }
                if (delta == 0) return@collect
                destIndex.intValue = index + delta
                sheetLive.value = true
                try {
                    animateTurn(turn, from = 0f, to = -delta.toFloat())
                    onPageChangeState.value(index + delta)
                } finally {
                    turn.floatValue = 0f
                    sheetLive.value = false
                }
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithContent {
                drawContent()
                drawPaperCurl(turn.floatValue, density)
            }
            .pointerInput(pageIndex, pageCount, enabled, sheetLive.value) {
                if (!enabled || sheetLive.value || pageCount <= 1) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop * 1.25f
                    val passed = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        val horizontal = abs(over.x) > abs(over.y) * 1.6f && abs(over.x) > slop
                        if (horizontal) change.consume()
                    } ?: return@awaitEachGesture
                    drag(passed.id) { change ->
                        change.consume()
                        val next = (turn.floatValue + change.positionChange().x / size.width)
                            .coerceIn(-1f, 1f)
                        turn.floatValue = next
                        val dest = leafDestination(pageIndex, pageCount, next)
                        if (dest != pageIndex) destIndex.intValue = dest
                        sheetLive.value = true
                    }
                    scope.launch {
                        try {
                            settleLiveTurn(turn, pageIndex, pageCount, onPageChange)
                        } finally {
                            turn.floatValue = 0f
                            sheetLive.value = false
                        }
                    }
                }
            },
    ) {
        if (sheetLive.value) {
            Box(Modifier.fillMaxSize()) { page(destIndex.intValue) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = RemainingPageShape(turn.floatValue)
                },
        ) { page(pageIndex) }
    }
}

private class RemainingPageShape(private val turn: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(remainingPageRect(size.width, size.height, turn))
}

internal fun remainingPageRect(width: Float, height: Float, turn: Float): Rect {
    val amount = abs(turn).coerceIn(0f, 1f)
    return when {
        turn < 0f -> Rect(0f, 0f, width * (1f - amount), height)
        turn > 0f -> Rect(width * amount, 0f, width, height)
        else -> Rect(0f, 0f, width, height)
    }
}

internal fun curlFoldX(width: Float, turn: Float): Float {
    val amount = abs(turn).coerceIn(0f, 1f)
    return if (turn <= 0f) width * (1f - amount) else width * amount
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaperCurl(
    turn: Float,
    density: Float,
) {
    val amount = abs(turn)
    if (amount < 0.012f || amount > 0.992f) return
    val fold = curlFoldX(size.width, turn)
    val curl = sin(amount * PI.toFloat()) * 52f * density
    if (curl < 2f) return
    val next = turn < 0f
    val left = if (next) fold else fold - curl
    val shadow = (amount * (1f - amount) * 4f).coerceIn(0f, 1f)

    val destShadow = if (next) {
        Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.22f * shadow), Color.Transparent),
            startX = fold,
            endX = fold + 64f * density,
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f * shadow)),
            startX = fold - 64f * density,
            endX = fold,
        )
    }
    drawRect(
        brush = destShadow,
        topLeft = Offset(if (next) fold else fold - 64f * density, 0f),
        size = Size(64f * density, size.height),
    )

    val curlBrush = Brush.horizontalGradient(
        colorStops = if (next) {
            arrayOf(
                0f to Color.Black.copy(alpha = 0.28f * shadow),
                0.18f to Ivory,
                0.42f to Parchment,
                0.72f to Fiber,
                1f to Color.Black.copy(alpha = 0.18f * shadow),
            )
        } else {
            arrayOf(
                0f to Color.Black.copy(alpha = 0.18f * shadow),
                0.28f to Fiber,
                0.58f to Parchment,
                0.82f to Ivory,
                1f to Color.Black.copy(alpha = 0.28f * shadow),
            )
        },
        startX = left,
        endX = left + curl,
    )
    drawRect(
        brush = curlBrush,
        topLeft = Offset(left, 0f),
        size = Size(curl, size.height),
    )
    val ridge = if (next) left + curl * 0.22f else left + curl * 0.78f
    drawRect(
        color = Color.White.copy(alpha = 0.35f * shadow),
        topLeft = Offset(ridge, 0f),
        size = Size(2f * density, size.height),
    )
}

internal fun leafDestination(pageIndex: Int, pageCount: Int, turn: Float): Int = when {
    turn < 0f -> (pageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
    turn > 0f -> (pageIndex - 1).coerceAtLeast(0)
    else -> pageIndex
}

private suspend fun settleLiveTurn(
    turn: androidx.compose.runtime.MutableFloatState,
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
) {
    val value = turn.floatValue
    val next = value < -0.16f && pageIndex < pageCount - 1
    val prev = value > 0.16f && pageIndex > 0
    when {
        next -> {
            animateTurn(turn, value, -1f)
            onPageChange(pageIndex + 1)
        }
        prev -> {
            animateTurn(turn, value, 1f)
            onPageChange(pageIndex - 1)
        }
        else -> animateTurn(turn, value, 0f)
    }
}

private suspend fun animateTurn(
    turn: androidx.compose.runtime.MutableFloatState,
    from: Float,
    to: Float,
    durationMs: Int = 680,
) {
    val startNanos = withFrameNanos { it }
    val durationNanos = durationMs * 1_000_000L
    while (true) {
        val fraction = ((withFrameNanos { it } - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
        turn.floatValue = from + (to - from) * PAGE_EASING.transform(fraction)
        if (fraction >= 1f) return
    }
}

private val PAGE_EASING = CubicBezierEasing(0.28f, 0.0f, 0.18f, 1f)
