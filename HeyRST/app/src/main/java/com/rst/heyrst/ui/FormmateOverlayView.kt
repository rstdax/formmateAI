package com.rst.heyrst.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rst.heyrst.engine.FormmateEngine
import com.rst.heyrst.model.ConversationState
import com.rst.heyrst.model.FormField
import kotlin.math.sin

/**
 * Redesigned compact assistant overlay matching the user's latest reference design:
 * - Low-profile, compact height
 * - Header: RST blue badge, "Hey RST", "• Listening...", and "✕"
 * - Title & Dual Localized Prompt: "Full name / সম্পূর্ণ নাম *", "What is your full name?", "আপোনার সম্পূর্ণ নাম কি?"
 * - Centered 9-bar colorful symmetrical waveform (speaking wave animation + voice reactive)
 * - "Say your answer..." capsule with mic icon
 * - Clean progress bar with "4 of 13 • 31%"
 * - Bottom Bar: Repeat, Back, [→ Next] blue pill button, and Submit
 */
@Composable
fun FormmateOverlayView(
    state: ConversationState,
    activeField: FormField?,
    fieldIndex: Int,
    totalFields: Int,
    assistantPrompt: String,
    userTranscript: String,
    lastConfirmedValue: String?,
    micVolumeRms: Float,
    isHindi: Boolean,
    onHeroTap: () -> Unit,
    onSkip: () -> Unit,
    onGoBack: () -> Unit,
    onRepeat: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val statusText = when (state) {
        ConversationState.SPEAKING -> if (isHindi) "Speaking..." else "Speaking..."
        ConversationState.LISTENING -> if (isHindi) "Listening..." else "Listening..."
        ConversationState.PROCESSING -> "Thinking..."
        ConversationState.WAITING_FOR_TAP -> "Tap to speak..."
        ConversationState.COMPLETED -> "Done"
        else -> "Ready"
    }

    val completedCount = if (state == ConversationState.COMPLETED) {
        maxOf(1, totalFields)
    } else {
        (fieldIndex + 1).coerceIn(1, maxOf(1, totalFields))
    }
    val safeTotal = maxOf(1, totalFields)
    val progressFraction = (completedCount.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val percent = (progressFraction * 100).toInt()

    // Title and localized sub-prompts
    val bilingualTitle = FormmateEngine.generateBilingualTitle(activeField)
    val localizedSubPrompt = FormmateEngine.generateLocalizedSubPrompt(activeField)

    // Outer compact card container
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF6FAFF) // Soft clean pale ice-blue
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFFE2EBF6),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // 1. Header: [RST] Hey RST       • Listening...      ✕
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Blue Badge "RST"
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFF1D4ED8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RST",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title: Hey RST
                Text(
                    text = "Hey RST",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Status indicator with colored dot: • Listening...
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (state == ConversationState.LISTENING) Color(0xFF10B981) else Color(0xFF0D9488),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        color = Color(0xFF0D9488),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Dismiss "✕"
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color(0xFF64748B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Bold Field Title: e.g. "Full name / সম্পূর্ণ নাম *"
            Text(
                text = bilingualTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 3. Spoken Question Prompt: "What is your full name?"
            Text(
                text = assistantPrompt,
                fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp,
                color = Color(0xFF1E293B),
                lineHeight = 19.sp
            )

            // 4. Vernacular Subtitle: "আপোনার সম্পূর্ণ নাম কি?"
            if (localizedSubPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = localizedSubPrompt,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp,
                    color = Color(0xFF475569)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Centered 9-Bar Symmetrical Waveform Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onHeroTap
                    ),
                contentAlignment = Alignment.Center
            ) {
                SymmetricalRainbowWaveform(
                    state = state,
                    micVolumeRms = micVolumeRms
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. "Say your answer..." Capsule with Mic Icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFEDF5FD))
                    .border(1.dp, Color(0xFFD6E6F8), RoundedCornerShape(22.dp))
                    .clickable(onClick = onHeroTap)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Blue Mic Vector Icon
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        val micColor = Color(0xFF2563EB)

                        // Capsule body
                        val capW = w * 0.38f
                        val capH = h * 0.52f
                        val capX = (w - capW) / 2f
                        drawRoundRect(
                            color = micColor,
                            topLeft = Offset(capX, h * 0.08f),
                            size = Size(capW, capH),
                            cornerRadius = CornerRadius(capW / 2f, capW / 2f)
                        )
                        // Arc
                        val arcPath = Path().apply {
                            val arcTop = h * 0.32f
                            val arcBottom = h * 0.70f
                            val arcLeft = (w - capW * 1.6f) / 2f
                            val arcRight = arcLeft + capW * 1.6f
                            moveTo(arcLeft, arcTop)
                            lineTo(arcLeft, arcBottom - capW * 0.35f)
                            cubicTo(arcLeft, arcBottom, arcRight, arcBottom, arcRight, arcBottom - capW * 0.35f)
                            lineTo(arcRight, arcTop)
                        }
                        drawPath(arcPath, micColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        // Stem & Base
                        drawLine(micColor, Offset(w / 2f, h * 0.70f), Offset(w / 2f, h * 0.88f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(micColor, Offset(w * 0.28f, h * 0.88f), Offset(w * 0.72f, h * 0.88f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = if (userTranscript.isNotBlank()) "\"$userTranscript\"" else "Say your answer...",
                        color = if (userTranscript.isNotBlank()) Color(0xFF0F172A) else Color(0xFF64748B),
                        fontSize = 13.5.sp,
                        fontWeight = if (userTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7. Progress Bar: Gradient Blue-to-Teal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Status: "4 of 13 • 31%"
            Text(
                text = "$completedCount of $safeTotal • $percent%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 8. Bottom Action Bar: Repeat | Back | [→ Next] | Submit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Action 1: Repeat (Refresh Icon)
                BottomNavAction(
                    label = "Repeat",
                    icon = NavIcon.REPEAT,
                    onClick = onRepeat
                )

                // Action 2: Back (Left Arrow)
                BottomNavAction(
                    label = "Back",
                    icon = NavIcon.BACK,
                    onClick = onGoBack
                )

                // Action 3: Blue Pill Button "→ Next"
                Button(
                    onClick = onSkip,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1D4ED8) // Royal Vivid Blue
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 9.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "→",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Next",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Action 4: Submit (Checkmark Icon)
                BottomNavAction(
                    label = "Submit",
                    icon = NavIcon.SUBMIT,
                    onClick = onSubmit
                )
            }
        }
    }
}

/**
 * 9-Bar Symmetrical Rainbow Waveform matching the exact screenshot:
 * Colors: Sky Blue, Cyan, Royal Blue, Indigo, Coral Red (tallest with glow), Amber, Yellow, Green, Cyan
 */
@Composable
private fun SymmetricalRainbowWaveform(
    state: ConversationState,
    micVolumeRms: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth voice reaction for LISTENING state
    val animatedRms by animateFloatAsState(
        targetValue = if (state == ConversationState.LISTENING) micVolumeRms.coerceIn(0f, 12f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rmsWave"
    )

    // 9 Symmetrical Rainbow Colors from screenshot
    val barColors = listOf(
        Color(0xFF38BDF8), // 1. Sky Blue
        Color(0xFF0284C7), // 2. Cyan Blue
        Color(0xFF2563EB), // 3. Royal Blue
        Color(0xFF4338CA), // 4. Indigo
        Color(0xFFEF4444), // 5. Coral Red (Tallest Center Bar)
        Color(0xFFF59E0B), // 6. Amber Orange
        Color(0xFFEAB308), // 7. Golden Yellow
        Color(0xFF10B981), // 8. Emerald Green
        Color(0xFF06B6D4)  // 9. Teal Cyan
    )

    // Symmetrical base heights (middle is highest)
    val baseHeights = listOf(14f, 22f, 32f, 40f, 48f, 40f, 32f, 22f, 14f)
    val offsets = listOf(0.0, 0.7, 1.4, 2.1, 2.8, 3.5, 4.2, 4.9, 5.6)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in barColors.indices) {
            val height = if (state == ConversationState.SPEAKING) {
                // Dynamic animated wave oscillation
                val wave = sin(phase + offsets[i]).toFloat()
                (baseHeights[i] + wave * 9f).coerceIn(10f, 52f)
            } else if (state == ConversationState.LISTENING) {
                // Voice adaptive expansion based on mic loudness
                val boost = (animatedRms / 12f) * 16f * (baseHeights[i] / 48f)
                (baseHeights[i] * 0.75f + boost).coerceIn(8f, 52f)
            } else {
                baseHeights[i] * 0.8f
            }

            Box(
                modifier = Modifier
                    .width(7.5.dp)
                    .height(height.dp)
                    .background(barColors[i], RoundedCornerShape(4.dp))
            )
        }
    }
}

private enum class NavIcon {
    REPEAT,
    BACK,
    SUBMIT
}

@Composable
private fun BottomNavAction(
    label: String,
    icon: NavIcon,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val color = Color(0xFF475569)
            val strokeW = 1.8.dp.toPx()

            when (icon) {
                NavIcon.REPEAT -> {
                    // Circular Repeat Arrow (⟳)
                    val arcRect = Size(w * 0.72f, h * 0.72f)
                    val topLeft = Offset((w - arcRect.width) / 2f, (h - arcRect.height) / 2f)
                    drawArc(
                        color = color,
                        startAngle = 30f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcRect,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Arrow head
                    drawLine(color, Offset(w * 0.78f, h * 0.28f), Offset(w * 0.90f, h * 0.46f), strokeWidth = strokeW, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.90f, h * 0.46f), Offset(w * 0.68f, h * 0.48f), strokeWidth = strokeW, cap = StrokeCap.Round)
                }
                NavIcon.BACK -> {
                    // Left Arrow (←)
                    drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.15f, h * 0.5f), strokeWidth = strokeW, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.45f, h * 0.22f), Offset(w * 0.15f, h * 0.5f), strokeWidth = strokeW, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.45f, h * 0.78f), Offset(w * 0.15f, h * 0.5f), strokeWidth = strokeW, cap = StrokeCap.Round)
                }
                NavIcon.SUBMIT -> {
                    // Checkmark (✓) inside subtle circular badge
                    val checkColor = Color(0xFF64748B)
                    drawCircle(color = Color(0xFFE8F0FA), radius = w * 0.48f)
                    val path = Path().apply {
                        moveTo(w * 0.30f, h * 0.50f)
                        lineTo(w * 0.45f, h * 0.65f)
                        lineTo(w * 0.72f, h * 0.35f)
                    }
                    drawPath(path, color = checkColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 11.5.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
    }
}
