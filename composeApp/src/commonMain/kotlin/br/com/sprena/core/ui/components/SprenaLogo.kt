package br.com.sprena.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LogoBlueLight = Color(0xFF00A8E8)
private val LogoBlueDark = Color(0xFF0077B6)
private val LogoBlueDeep = Color(0xFF023E8A)
private val LogoOrangeLight = Color(0xFFFFA94D)
private val LogoOrangeDark = Color(0xFFE87B35)

@Composable
fun SprenaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = w * 0.45f

        drawCircle(
            brush =
                Brush.linearGradient(
                    colors = listOf(LogoBlueDark, LogoBlueDeep),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            radius = radius,
            center = Offset(cx, cy),
        )

        drawWaveUpper(cx, cy, radius)
        drawWaveLower(cx, cy, radius)
        drawHighlight(cx, cy, radius)
    }
}

private fun DrawScope.drawWaveUpper(
    cx: Float,
    cy: Float,
    radius: Float,
) {
    val path =
        Path().apply {
            moveTo(cx - radius * 0.45f, cy - radius * 0.6f)
            cubicTo(
                cx - radius * 0.1f,
                cy - radius * 0.5f,
                cx + radius * 0.1f,
                cy - radius * 0.1f,
                cx - radius * 0.15f,
                cy + radius * 0.05f,
            )
            cubicTo(
                cx - radius * 0.35f,
                cy + radius * 0.15f,
                cx - radius * 0.1f,
                cy + radius * 0.3f,
                cx + radius * 0.35f,
                cy + radius * 0.1f,
            )
        }
    drawPath(
        path = path,
        brush =
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f), LogoBlueLight),
                start = Offset(cx - radius * 0.5f, cy - radius * 0.6f),
                end = Offset(cx + radius * 0.4f, cy + radius * 0.2f),
            ),
        style = Stroke(width = radius * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawWaveLower(
    cx: Float,
    cy: Float,
    radius: Float,
) {
    val path =
        Path().apply {
            moveTo(cx + radius * 0.1f, cy - radius * 0.15f)
            cubicTo(
                cx + radius * 0.35f,
                cy + radius * 0.05f,
                cx + radius * 0.5f,
                cy + radius * 0.35f,
                cx + radius * 0.2f,
                cy + radius * 0.55f,
            )
        }
    drawPath(
        path = path,
        brush =
            Brush.linearGradient(
                colors = listOf(LogoOrangeLight, LogoOrangeDark),
                start = Offset(cx, cy - radius * 0.2f),
                end = Offset(cx + radius * 0.3f, cy + radius * 0.6f),
            ),
        style = Stroke(width = radius * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawHighlight(
    cx: Float,
    cy: Float,
    radius: Float,
) {
    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                center = Offset(cx - radius * 0.25f, cy - radius * 0.25f),
                radius = radius * 0.6f,
            ),
    )
}
