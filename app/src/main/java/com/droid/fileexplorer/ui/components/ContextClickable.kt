package com.droid.fileexplorer.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks if the pointer event is a context menu trigger:
 * - Mouse secondary (right) button
 * - Mouse back button (standard right-click mapping on some Android USB/BT mice)
 * - Mouse tertiary (middle) or stylus context button
 * - Alt + Primary click (standard desktop shortcut)
 */
fun PointerEvent.isContextMenuEvent(): Boolean {
    if (buttons.isSecondaryPressed || buttons.isBackPressed || buttons.isTertiaryPressed) {
        return true
    }
    val motion = motionEvent
    if (motion != null) {
        val buttonState = motion.buttonState
        if ((buttonState and MotionEvent.BUTTON_SECONDARY != 0) ||
            (buttonState and MotionEvent.BUTTON_BACK != 0) ||
            (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0)
        ) {
            return true
        }
        val actionMasked = motion.actionMasked
        if (actionMasked == MotionEvent.ACTION_BUTTON_PRESS || actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
            if (motion.actionButton == MotionEvent.BUTTON_SECONDARY || motion.actionButton == MotionEvent.BUTTON_BACK) {
                return true
            }
        }
        if (motion.isButtonPressed(MotionEvent.BUTTON_SECONDARY) || motion.isButtonPressed(MotionEvent.BUTTON_BACK)) {
            return true
        }
    }
    if (keyboardModifiers.isAltPressed && (buttons.isPrimaryPressed || (motion != null && (motion.buttonState and MotionEvent.BUTTON_PRIMARY != 0)))) {
        return true
    }
    return false
}

/**
 * Detects drag gestures while completely ignoring secondary / context-menu mouse clicks.
 */
suspend fun PointerInputScope.detectPrimaryDragGestures(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (currentEvent.isContextMenuEvent()) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.changes.all { !it.pressed }) break
        }
        return@awaitEachGesture
    }
    var drag: PointerInputChange? = null
    var overSlop = Offset.Zero
    do {
        drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        }
    } while (drag != null && !drag.isConsumed)

    if (drag != null) {
        onDragStart.invoke(drag.position)
        onDrag(drag, overSlop)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) break
            if (!change.pressed) {
                onDragEnd()
                break
            }
            val dragAmount = change.positionChange()
            change.consume()
            onDrag(change, dragAmount)
        }
    }
}

fun Modifier.contextClickable(
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onContextMenu: () -> Unit
): Modifier = contextClickable(
    onClickWithModifiers = { _, _ -> onClick() },
    onDoubleClick = onDoubleClick,
    onContextMenu = onContextMenu,
    onContextMenuWithOffset = null
)

fun Modifier.contextClickable(
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onContextMenuWithOffset: (Offset) -> Unit
): Modifier = contextClickable(
    onClickWithModifiers = { _, _ -> onClick() },
    onDoubleClick = onDoubleClick,
    onContextMenu = {},
    onContextMenuWithOffset = onContextMenuWithOffset
)

fun Modifier.contextClickable(
    onClickWithModifiers: (isCtrl: Boolean, isShift: Boolean) -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onContextMenu: () -> Unit = {},
    onContextMenuWithOffset: ((Offset) -> Unit)? = null
): Modifier = composed {
    val currentOnClickWithModifiers by rememberUpdatedState(onClickWithModifiers)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentOnContextMenu by rememberUpdatedState(onContextMenu)
    val currentOnContextMenuWithOffset by rememberUpdatedState(onContextMenuWithOffset)

    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current

    var lastClickTime by remember { mutableLongStateOf(0L) }
    var lastClickPos by remember { mutableStateOf(Offset.Zero) }

    this
        .indication(interactionSource, ripple())
        .semantics { role = Role.Button }
        .pointerInput(Unit) {
            awaitEachGesture {
                // 1. Wait for pointer down
                var downChange: PointerInputChange? = null
                while (downChange == null) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.any { it.isConsumed }) continue

                    val isDown = event.changes.any { it.changedToDown() } ||
                        (event.type == PointerEventType.Press && event.changes.any { it.pressed }) ||
                        event.isContextMenuEvent()

                    if (isDown) {
                        downChange = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
                    }
                }

                val down = downChange
                val isContext = currentEvent.isContextMenuEvent()

                if (isContext) {
                    // Right Click / Context Menu
                    down.consume()
                    val downPos = down.position

                    // Trigger Context Menu callbacks immediately
                    currentOnContextMenuWithOffset?.invoke(downPos)
                    currentOnContextMenu()

                    // Consume all remaining changes (moves, release) until release
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { it.consume() }
                        val hasReleased = event.type == PointerEventType.Release ||
                            event.changes.all { !it.pressed } ||
                            !event.isContextMenuEvent()
                        if (hasReleased) break
                    }
                } else {
                    // Primary Click (Left click or Touch)
                    val downPos = down.position
                    val isTouch = down.type == PointerType.Touch
                    val isCtrl = currentEvent.keyboardModifiers.isCtrlPressed
                    val isShift = currentEvent.keyboardModifiers.isShiftPressed

                    val press = PressInteraction.Press(downPos)
                    scope.launch { interactionSource.emit(press) }

                    val touchSlop = viewConfiguration.touchSlop
                    var isDrag = false
                    var isLongPress = false
                    var upChange: PointerInputChange? = null

                    if (isTouch) {
                        // For touch: detect long press or tap
                        val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dist = (change.position - downPos).getDistance()
                                if (dist > touchSlop) {
                                    isDrag = true
                                    break
                                }
                                if (!change.pressed) {
                                    upChange = change
                                    break
                                }
                            }
                        }

                        if (result == null && !isDrag) {
                            // Long press triggered on touch!
                            isLongPress = true
                            scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                            currentOnContextMenuWithOffset?.invoke(downPos)
                            currentOnContextMenu()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                                if (event.changes.all { !it.pressed }) break
                            }
                        }
                    } else {
                        // For mouse: left click
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dist = (change.position - downPos).getDistance()
                            if (dist > touchSlop) {
                                isDrag = true
                                break
                            }
                            if (!change.pressed) {
                                upChange = change
                                break
                            }
                        }
                    }

                    if (!isLongPress && !isDrag && upChange != null) {
                        upChange.consume()
                        scope.launch { interactionSource.emit(PressInteraction.Release(press)) }

                        if (currentOnDoubleClick != null) {
                            val now = System.currentTimeMillis()
                            val timeSinceLast = now - lastClickTime
                            val distFromLast = (downPos - lastClickPos).getDistance()
                            if (timeSinceLast < viewConfiguration.doubleTapTimeoutMillis && distFromLast < touchSlop * 2f) {
                                lastClickTime = 0L
                                currentOnDoubleClick?.invoke()
                            } else {
                                lastClickTime = now
                                lastClickPos = downPos
                                currentOnClickWithModifiers(isCtrl, isShift)
                            }
                        } else {
                            currentOnClickWithModifiers(isCtrl, isShift)
                        }
                    } else if (isDrag) {
                        scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                    }
                }
            }
        }
}
