package com.example.lifelayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifelayer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    // Staggered animations for the 5 pyramid layers of the logo mark
    val layerAnims = List(5) { remember { Animatable(0f) } }
    
    // Scale and opacity of the logo title
    val titleAlpha = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.9f) }
    
    // Progress bar value driven by typewriter text progress
    var targetProgress by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "BootProgress"
    )
    
    // Boot sequence messages
    val bootMessages = listOf(
        "LFLR // CORE ENGINE INITIALIZATION...",
        "SYS // CONNECTING DATABASE STORAGE...",
        "SYS // LOADING DEPLOYED MISSIONS...",
        "SYS // GRID MONOLITH SYNC ACTIVE...",
        "LFLR // ESTABLISHING TELEMETRY LINK...",
        "SYSTEM ONLINE // WELCOME COMMANDER."
    )
    
    val displayedLogs = remember { mutableStateListOf<String>() }
    var currentLogLine by remember { mutableStateOf("") }
    var cursorVisible by remember { mutableStateOf(true) }

    // Scanline vertical offset animation
    val infiniteTransition = rememberInfiniteTransition(label = "ScanlineGlow")
    val scanlineY = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanlineY"
    )

    // Breathing glow animation
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = SineBorder),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoGlow"
    )

    // Charging sweep pulse animation
    val sweepProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepProgress"
    )

    // Cursor blinking effect
    LaunchedEffect(Unit) {
        while (true) {
            cursorVisible = !cursorVisible
            delay(400)
        }
    }

    // Logo and Title staggered startup sequence
    LaunchedEffect(Unit) {
        // Step 1: Animate layers sequentially from bottom to top
        for (i in 0..4) {
            launch {
                delay(i * 200L) // Staggered delay
                layerAnims[i].animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
        
        // Wait for logo to build, then fade in title
        delay(1000)
        launch {
            titleAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
        launch {
            titleScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    // Typewriter terminal boot-up simulation and progress bar
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val totalDuration = 4000L
        val initialDelay = 300L
        val typingDuration = 3300L

        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = now - startTime
            if (elapsed >= totalDuration) {
                targetProgress = 1f
                val expectedLogs = bootMessages.subList(0, bootMessages.size - 1)
                if (displayedLogs.size != expectedLogs.size) {
                    displayedLogs.clear()
                    displayedLogs.addAll(expectedLogs)
                }
                currentLogLine = bootMessages.last()
                break
            }

            // Calculate logs state based on elapsed
            val elapsedLogTime = elapsed - initialDelay
            if (elapsedLogTime < 0) {
                targetProgress = 0f
                if (displayedLogs.isNotEmpty()) {
                    displayedLogs.clear()
                }
                currentLogLine = ""
            } else if (elapsedLogTime >= typingDuration) {
                targetProgress = 1f
                val expectedLogs = bootMessages.subList(0, bootMessages.size - 1)
                if (displayedLogs.size != expectedLogs.size) {
                    displayedLogs.clear()
                    displayedLogs.addAll(expectedLogs)
                }
                currentLogLine = bootMessages.last()
            } else {
                val messageCount = bootMessages.size
                val messageBlockTimeMs = typingDuration / messageCount // 550ms
                val linePauseTimeMs = 100L
                val typeTimePerMessageMs = messageBlockTimeMs - linePauseTimeMs // 450ms

                val msgIndex = (elapsedLogTime / messageBlockTimeMs).toInt().coerceIn(0, messageCount - 1)
                val msgElapsedTime = elapsedLogTime % messageBlockTimeMs

                val expectedLogs = bootMessages.subList(0, msgIndex)
                if (displayedLogs.size != expectedLogs.size) {
                    displayedLogs.clear()
                    displayedLogs.addAll(expectedLogs)
                }

                val activeMessage = bootMessages[msgIndex]
                if (msgElapsedTime < typeTimePerMessageMs) {
                    val fraction = msgElapsedTime.toFloat() / typeTimePerMessageMs
                    val charCount = (activeMessage.length * fraction).toInt().coerceIn(0, activeMessage.length)
                    currentLogLine = activeMessage.substring(0, charCount)
                    targetProgress = (msgIndex + (charCount.toFloat() / activeMessage.length)) / messageCount
                } else {
                    currentLogLine = activeMessage
                    targetProgress = (msgIndex + 1f) / messageCount
                }
            }

            delay(16) // Update around 60fps
        }
        
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnyxBlack)
    ) {
        // 1. Grid Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val gridColor = GhostSlateBorder.copy(alpha = 0.15f)
            
            // Vertical grid lines
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += gridSpacing
            }
            
            // Horizontal grid lines
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += gridSpacing
            }

            // Radial backdrop glow behind logo
            val radialGlowRadius = 250.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CommandGold.copy(alpha = 0.08f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height * 0.4f),
                    radius = radialGlowRadius
                ),
                radius = radialGlowRadius,
                center = Offset(size.width / 2f, size.height * 0.4f)
            )

            // Scanning light scanline
            val scanlineHeight = 4.dp.toPx()
            val scanlineCurrentY = size.height * scanlineY.value
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CommandGold.copy(alpha = 0.06f),
                        CommandGold.copy(alpha = 0.12f),
                        CommandGold.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    startY = scanlineCurrentY - scanlineHeight * 2,
                    endY = scanlineCurrentY + scanlineHeight * 2
                ),
                topLeft = Offset(0f, scanlineCurrentY - scanlineHeight * 2),
                size = Size(size.width, scanlineHeight * 4)
            )
        }

        // 2. Main Content Column (Logo, Title, Telemetry)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Spacer to push logo down
            Spacer(modifier = Modifier.height(60.dp))

            // Logo & Title Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Staggered Layered Logo Mark
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val layerCount = 5
                        val layerHeight = 12.dp.toPx()
                        val layerGap = 8.dp.toPx()
                        val totalHeight = layerCount * layerHeight + (layerCount - 1) * layerGap
                        val startY = (size.height - totalHeight) / 2f

                        // Draw glowing base aura behind the entire logo stack
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CommandGold.copy(alpha = 0.2f * glowIntensity),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, size.height / 2f),
                                radius = 70.dp.toPx()
                            ),
                            radius = 70.dp.toPx(),
                            center = Offset(centerX, size.height / 2f)
                        )

                        for (i in 0 until layerCount) {
                            val animProgress = layerAnims[i].value
                            if (animProgress <= 0f) continue

                            // 0 is bottom layer, 4 is top layer
                            val widthRatio = 1.0f - (i * 0.18f) // 1.0, 0.82, 0.64, 0.46, 0.28
                            val maxLayerWidth = size.width * 0.8f
                            val targetWidth = maxLayerWidth * widthRatio
                            
                            // Width scales as it loads
                            val currentWidth = targetWidth * animProgress
                            
                            // Y position (bottom-most is drawn at the bottom)
                            val yOffset = startY + (4 - i) * (layerHeight + layerGap)
                            
                            // Slide up slightly as it builds
                            val currentY = yOffset - (1f - animProgress) * 16.dp.toPx()
                            
                            val left = centerX - currentWidth / 2f
                            val top = currentY
                            
                            // 1. Draw soft drop shadow for 3D depth
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.5f * animProgress),
                                topLeft = Offset(left - 2.dp.toPx(), top + 3.dp.toPx()),
                                size = Size(currentWidth + 4.dp.toPx(), layerHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            // 2. Draw outer ambient glow
                            drawRoundRect(
                                color = CommandGold.copy(alpha = 0.08f * animProgress * glowIntensity),
                                topLeft = Offset(left - 4.dp.toPx(), top - 4.dp.toPx()),
                                size = Size(currentWidth + 8.dp.toPx(), layerHeight + 8.dp.toPx()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )

                            // 3. Draw metallic plate body
                            val plateGradient = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFB89332), // Brass gold
                                    CommandGold,       // Main gold
                                    Color(0xFFFFF5D0), // Highlight shimmer
                                    CommandGold,       // Main gold
                                    Color(0xFF9E781B)  // Shadow gold
                                ),
                                start = Offset(left, top),
                                end = Offset(left + currentWidth, top + layerHeight)
                            )
                            drawRoundRect(
                                brush = plateGradient,
                                topLeft = Offset(left, top),
                                size = Size(currentWidth, layerHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                alpha = animProgress
                            )

                            // 4. Draw outline border highlight
                            val borderGradient = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFFDE8), // Bright gold top rim
                                    CommandGold.copy(alpha = 0.4f),
                                    Color(0xFF9E781B).copy(alpha = 0.2f) // Darker bottom rim
                                ),
                                startY = top,
                                endY = top + layerHeight
                            )
                            drawRoundRect(
                                brush = borderGradient,
                                topLeft = Offset(left, top),
                                size = Size(currentWidth, layerHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx()),
                                alpha = animProgress
                            )

                            // 5. Draw charging pulse highlight
                            // Pulse moves from bottom (i = 0) to top (i = 4)
                            val pulseCenter = sweepProgress.value * 1.4f - 0.2f // range [-0.2, 1.2]
                            val layerPos = i / 4f // bottom is 0.0, top is 1.0
                            val dist = Math.abs(pulseCenter - layerPos)
                            if (dist < 0.15f) {
                                val pulseAlpha = (1f - (dist / 0.15f)) * 0.7f * animProgress
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = pulseAlpha),
                                            Color.Transparent
                                        ),
                                        startX = left,
                                        endX = left + currentWidth
                                    ),
                                    topLeft = Offset(left, top),
                                    size = Size(currentWidth, layerHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    alpha = animProgress
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Title and Subtitle with Entry Animations
                Column(
                    modifier = Modifier.graphicsLayer(
                        alpha = titleAlpha.value,
                        scaleX = titleScale.value,
                        scaleY = titleScale.value
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LIFELAYER",
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 16.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HIGH-STAKES GOAL PROTOCOL",
                        color = CommandGoldMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Bottom Console Terminal Logs & Loading State
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Boot log window
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    displayedLogs.forEach { log ->
                        Text(
                            text = log,
                            color = CommandGoldMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                    if (currentLogLine.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentLogLine,
                                color = CommandGold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                            if (cursorVisible) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp, 10.dp)
                                        .background(CommandGold)
                                )
                            }
                        }
                    } else if (displayedLogs.size < bootMessages.size && cursorVisible) {
                        // Show blinking cursor even when between lines
                        Box(
                            modifier = Modifier
                                .size(6.dp, 10.dp)
                                .background(CommandGoldMuted)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = CommandGold,
                    trackColor = GhostSlateBorder
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SECURE LINK: ESTABLISHED",
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = CommandGold,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// SineInterpolator-like Easing for the glowing effect
val SineBorder = Easing { fraction ->
    kotlin.math.sin(fraction * Math.PI / 2).toFloat()
}
