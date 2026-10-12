package com.droid.fileexplorer.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalFoundationApi::class)
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

@OptIn(ExperimentalFoundationApi::class)
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

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.contextClickable(
    onClickWithModifiers: (isCtrl: Boolean, isShift: Boolean) -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onContextMenu: () -> Unit = {},
    onContextMenuWithOffset: ((Offset) -> Unit)? = null
): Modifier = composed {
    var lastIsCtrl by remember { mutableStateOf(false) }
    var lastIsShift by remember { mutableStateOf(false) }
    var lastTouchPos by remember { mutableStateOf(Offset.Zero) }

    val currentOnClickWithModifiers by rememberUpdatedState(onClickWithModifiers)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentOnContextMenu by rememberUpdatedState(onContextMenu)
    val currentOnContextMenuWithOffset by rememberUpdatedState(onContextMenuWithOffset)

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Main)
                    val motionEvent = downEvent.motionEvent
                    val isSecondaryDown = downEvent.buttons.isSecondaryPressed ||
                        (motionEvent != null && (
                            (motionEvent.buttonState and MotionEvent.BUTTON_SECONDARY != 0) ||
                            (motionEvent.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && motionEvent.actionButton == MotionEvent.BUTTON_SECONDARY) ||
                            motionEvent.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                        )) ||
                        (downEvent.keyboardModifiers.isAltPressed && downEvent.buttons.isPrimaryPressed)

                    val firstChange = downEvent.changes.firstOrNull()
                    if (firstChange != null) {
                        lastTouchPos = firstChange.position
                    }

                    if (isSecondaryDown && !downEvent.changes.any { it.isConsumed }) {
                        val downPos = firstChange?.position ?: Offset.Zero
                        downEvent.changes.forEach { it.consume() }
                        var movedFar = false

                        // Await release to prevent premature popup dismissal from the pending UP event
                        while (true) {
                            val nextEvent = awaitPointerEvent(PointerEventPass.Main)
                            nextEvent.changes.forEach { change ->
                                if ((change.position - downPos).getDistance() > 14f) {
                                    movedFar = true
                                }
                                change.consume()
                            }
                            if (nextEvent.changes.all { !it.pressed }) {
                                break
                            }
                        }

                        if (!movedFar) {
                            currentOnContextMenuWithOffset?.invoke(downPos)
                            currentOnContextMenu()
                        }
                    } else {
                        if (downEvent.buttons.isPrimaryPressed || downEvent.changes.any { it.pressed }) {
                            lastIsCtrl = downEvent.keyboardModifiers.isCtrlPressed
                            lastIsShift = downEvent.keyboardModifiers.isShiftPressed
                        }
                    }
                }
            }
        }
        .combinedClickable(
            onClick = { currentOnClickWithModifiers(lastIsCtrl, lastIsShift) },
            onDoubleClick = currentOnDoubleClick,
            onLongClick = {
                currentOnContextMenuWithOffset?.invoke(lastTouchPos)
                currentOnContextMenu()
            }
        )
}

