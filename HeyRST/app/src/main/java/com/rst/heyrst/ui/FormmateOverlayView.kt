package com.rst.heyrst.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rst.heyrst.model.ConversationState
import com.rst.heyrst.model.FormField

/**
 * Apple Liquid Glass Assistant Floating Overlay
 * Faithfully mirrors Formmate AI sidepanel design and voice feedback states.
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
    onToggleLanguage: () -> Unit,
    onDismiss: () -> Unit
) {
    // Breathing pulse for SPEAKING / LISTENING states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Spin animation for PROCESSING state
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    // Mic reactivity scale when LISTENING
    val micScale = if (state == ConversationState.LISTENING) {
        1f + (micVolumeRms.coerceIn(0f, 12f) / 16f)
    } else {
        breathingScale
    }

    // Dynamic glow palette based on conversation state
    val heroColors = when (state) {
        ConversationState.SPEAKING -> listOf(Color(0xFF7C3AED), Color(0xFFA855F7), Color(0xFFC084FC)) // Radiant Amethyst
        ConversationState.LISTENING -> listOf(Color(0xFF06B6D4), Color(0xFF0EA5E9), Color(0xFF10B981)) // Electric Cyan & Emerald
        ConversationState.PROCESSING -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6)) // Radiant Spin
        ConversationState.COMPLETED -> listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF34D399)) // Emerald Success
        else -> listOf(Color(0xFF6366F1), Color(0xFF4F46E5), Color(0xFF818CF8)) // Idle Indigo
    }

    val stateBadgeColor by animateColorAsState(
        targetValue = when (state) {
            ConversationState.SPEAKING -> Color(0xFFA855F7)
            ConversationState.LISTENING -> Color(0xFF06B6D4)
            ConversationState.PROCESSING -> Color(0xFFF59E0B)
            ConversationState.COMPLETED -> Color(0xFF10B981)
            else -> Color(0xFF94A3B8)
        },
        label = "badgeColor"
    )

    val stateLabel = when (state) {
        ConversationState.SPEAKING -> if (isHindi) "बोल रहा है..." else "Speaking..."
        ConversationState.LISTENING -> if (isHindi) "सुन रहा है..." else "Listening..."
        ConversationState.PROCESSING -> if (isHindi) "सोच रहा है..." else "Processing..."
        ConversationState.WAITING_FOR_TAP -> if (isHindi) "बोलने के लिए टैप करें" else "Tap to speak"
        ConversationState.COMPLETED -> if (isHindi) "पूर्ण हुआ!" else "Completed!"
        ConversationState.SCANNING -> if (isHindi) "फ़ॉर्म स्कैन हो रहा है..." else "Scanning form..."
        else -> "Hey RST"
    }

    // Translucent Liquid Glass Background Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF20F172A) // 95% deep slate glass
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0x66FFFFFF), Color(0x1A6366F1), Color(0x33000000))
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(stateBadgeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stateLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Language Switcher Pill
                    TextButton(
                        onClick = onToggleLanguage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .background(Color(0x266366F1), RoundedCornerShape(16.dp))
                            .height(28.dp)
                    ) {
                        Text(
                            text = if (isHindi) "EN" else "हिन्दी",
                            color = Color(0xFFE0E7FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Iridescent Voice Hero Circle
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onHeroTap
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer glowing aura
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(micScale)
                        .background(
                            brush = Brush.radialGradient(heroColors),
                            shape = CircleShape
                        )
                )

                // Inner liquid glass core
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .rotate(if (state == ConversationState.PROCESSING) spinAngle else 0f)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(2.dp, Color(0x80FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state == ConversationState.COMPLETED) "✓" else "RST",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Assistant Question Bubble (Apple Liquid Glass Speech Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (isHindi) "सहायक प्रश्न" else "Assistant Prompt",
                        color = Color(0xFFA5B4FC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = assistantPrompt,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp
                    )
                }
            }

            // "You Said" Live Transcript Card
            AnimatedVisibility(visible = userTranscript.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x2606B6D4)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x4006B6D4), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHindi) "आपने कहा: " else "You said: ",
                            color = Color(0xFF67E8F9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "\"$userTranscript\"",
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Current Field Details Card (if active)
            if (activeField != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Field ${fieldIndex + 1} of $totalFields",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                            if (activeField.required) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Required",
                                    color = Color(0xFFF87171),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = activeField.label,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Value status pill
                    if (lastConfirmedValue != null) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x3310B981), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✓ Filled",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Pills (Tap to Speak, Skip, Back, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AssistPillButton(
                    text = if (state == ConversationState.LISTENING) "Pause" else "Speak",
                    onClick = onHeroTap
                )
                AssistPillButton(
                    text = "Repeat",
                    onClick = onRepeat
                )
                AssistPillButton(
                    text = "Back",
                    onClick = onGoBack
                )
                AssistPillButton(
                    text = "Skip",
                    onClick = onSkip
                )
            }
        }
    }
}

@Composable
private fun AssistPillButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x33FFFFFF)
        ),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
