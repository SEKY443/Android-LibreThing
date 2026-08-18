package com.example.android_go_librespot.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure-black full-screen overlay ("fake sleep") to protect OLED panels from burn-in while
 * the service keeps running underneath. Dismissed by a double tap or an upward swipe, both
 * deliberately not a single tap so it isn't dismissed by an accidental touch.
 */
@Composable
fun BlackScreenOverlay(onDismiss: () -> Unit) {
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val swipeDismissThresholdPx = -150f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDismiss() })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragAccum += amount
                        if (dragAccum < swipeDismissThresholdPx) onDismiss()
                    },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = "Double-tap or swipe up to wake",
            color = Color(0xFF262626),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp),
        )
    }
}
