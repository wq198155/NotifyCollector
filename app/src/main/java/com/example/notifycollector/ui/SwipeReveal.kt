package com.example.notifycollector.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Text
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 一条左滑操作菜单所需的信息。点击后会自动收起菜单（animateTo(0f)）。
 * [color] 为该操作大色块的背景色；省略（[Color.Unspecified]）时回退为 [MaterialTheme.colorScheme.primary]。
 */
data class SwipeAction(
    val label: String,
    val color: Color = Color.Unspecified,
    val onClick: () -> Unit
)

/**
 * 通用「左滑露出操作菜单」行容器。
 * - 默认整行可点击 [onTap]；长按触发 [onLongPress]（可选，用于进入排序模式）。
 * - 向左滑动露出 [actions] 菜单；松手时按阈值**吸附**：过半则展开，否则回弹。
 * - 点击某个 [SwipeAction] 后自动收起。
 *
 * 菜单布局（仿微信）：操作区固定占整行宽度的 **1/3**（2 个按钮各 1/6），
 * 高度铺满整行；左滑展开后**左侧 2/3 仍显示原内容**，按住左侧内容往右滑即收起还原。
 * 菜单宽度由 [BoxWithConstraints] 取行宽后固定为 1/3，不随文字长短变化。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    actions: List<SwipeAction>,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    var actionWidthPx by remember { mutableStateOf(0f) }
    // 实测内容行高，强制让右滑色块高度==列表行高（BoxWithConstraints 在 LazyColumn 中
    // maxHeight 为无穷/极大，fillMaxHeight 会失真变矮，故改为「以内容实测高度为准」）。
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 操作菜单固定为整行宽度的 1/3（与文字长短无关）
        val menuWidth = maxWidth / 3f
        // 布局前就先给出像素值兜底，避免首次测量前的 0 宽竞态；测量到后以其为准
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val effectiveMax = if (actionWidthPx > 0f) actionWidthPx else menuWidthPx

        // 操作菜单仅在「已左滑展开」时可见：静止(未滑动)时强制隐藏，
        // 避免前景内容因已读/过期被置半透明时，下层按钮透过前景造成重叠。
        val menuAlpha = run {
            val half = effectiveMax / 2f
            ((-offset.floatValue) / half).coerceIn(0f, 1f)
        }

        fun animateTo(target: Float) {
            scope.launch {
                animate(
                    initialValue = offset.floatValue,
                    targetValue = target,
                    animationSpec = tween(200)
                ) { v, _ -> offset.floatValue = v }
            }
        }

        fun thresholdPx() = effectiveMax

        // 背景操作菜单：固定宽度 = 1/3 行宽，高度=实测内容行高（与列表行等高）
        val menuHeightMod = if (rowHeightPx > 0)
            Modifier.height(with(density) { rowHeightPx.toDp() }) else Modifier
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(menuWidth)
                .then(menuHeightMod)
                .alpha(menuAlpha)
                .onGloballyPositioned { coordinates ->
                    actionWidthPx = coordinates.size.width.toFloat()
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(Modifier.fillMaxHeight().fillMaxWidth()) {
                actions.forEach { a ->
                    val bg = if (a.color == Color.Unspecified)
                        MaterialTheme.colorScheme.primary else a.color
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(bg)
                            .clickable {
                                a.onClick()
                                animateTo(0f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            a.label,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
        // 前景内容：可左滑、可点击、可长按；左滑时整块左移露出右侧菜单，
        // 按住左侧内容往右滑即收起还原。实测其行高供右滑色块等高对齐。
        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowHeightPx = it.size.height }
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offset.floatValue =
                                (offset.floatValue + dragAmount).coerceIn(-effectiveMax, 0f)
                        },
                        onDragEnd = {
                            animateTo(if (offset.floatValue < -effectiveMax / 2) -effectiveMax else 0f)
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (offset.floatValue < -(thresholdPx() / 2)) animateTo(0f) else onTap()
                    },
                    onLongClick = { onLongPress?.invoke() }
                )
        ) {
            content()
        }
    }
}
