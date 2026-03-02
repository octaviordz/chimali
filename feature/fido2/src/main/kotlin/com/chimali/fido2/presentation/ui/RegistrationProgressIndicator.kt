package com.chimali.fido2.presentation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * T066 — Registration Progress Indicator.
 *
 * Animated multi-ring indicator shown during the cryptographic registration phase.
 * Uses a pulsating circular animation to indicate BLE/HID communication + key generation
 * is happening in the background.
 *
 * @param message    Status message to display beneath the indicator.
 * @param size       Diameter of the indicator canvas.
 * @param strokeWidth Width of the arc strokes.
 */
@Composable
fun RegistrationProgressIndicator(
    message: String = "Registering passkey…",
    size: Dp = 120.dp,
    strokeWidth: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fido2-progress")

    // Outer arc rotation
    val outerRotation by infiniteTransition.animateFloat(
        initialValue   = 0f,
        targetValue    = 360f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer-rotation"
    )

    // Inner arc rotation (counter-clockwise, slightly faster)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue   = 0f,
        targetValue    = -360f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner-rotation"
    )

    // Pulsating alpha for the center dot
    val centerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center-alpha"
    )

    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary  = MaterialTheme.colorScheme.tertiary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier            = Modifier
            .padding(32.dp)
            .semantics { contentDescription = "Registration in progress" }
    ) {
        Canvas(
            modifier = Modifier.size(size)
        ) {
            val center     = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius     = (size.toPx() / 2f) - strokeWidth.toPx()
            val innerRadius = radius * 0.65f
            val stroke     = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val innerStroke = Stroke(width = (strokeWidth.toPx() * 0.7f), cap = StrokeCap.Round)

            // Outer ring
            drawArc(
                color        = primary,
                startAngle   = outerRotation,
                sweepAngle   = 240f,
                useCenter    = false,
                style        = stroke,
                topLeft      = Offset(center.x - radius, center.y - radius),
                size         = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Inner ring (counter-clockwise)
            drawArc(
                color      = secondary,
                startAngle = innerRotation,
                sweepAngle = 200f,
                useCenter  = false,
                style      = innerStroke,
                topLeft    = Offset(center.x - innerRadius, center.y - innerRadius),
                size       = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2)
            )

            // Pulsating center dot
            drawCircle(
                color  = tertiary.copy(alpha = centerAlpha),
                radius = strokeWidth.toPx() * 1.5f,
                center = center
            )
        }

        Text(
            text       = message,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground
        )

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.6f),
            color    = MaterialTheme.colorScheme.primary
        )
    }
}
