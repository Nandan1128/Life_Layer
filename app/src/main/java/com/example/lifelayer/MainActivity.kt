package com.example.lifelayer

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Color as AndroidColor
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.lifelayer.data.MissionEntity
import com.example.lifelayer.service.MoriWallpaperService
import com.example.lifelayer.ui.MainViewModel
import com.example.lifelayer.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeLayerTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun LifeLayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CommandGold,
            background = OnyxBlack,
            surface = GhostSlate
        ),
        content = content
    )
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val activeMissions by viewModel.activeMissions.collectAsState()
    MainScreenContent(
        activeMissions = activeMissions,
        onDeployMission = { content, days -> viewModel.deployMission(content, days) },
        onCompleteMission = { mission -> viewModel.completeMission(mission) }
    )
}

@Composable
fun MainScreenContent(
    activeMissions: List<MissionEntity>,
    onDeployMission: (String, Int) -> Unit,
    onCompleteMission: (MissionEntity) -> Unit
) {
    val context = LocalContext.current
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showNewGoalDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Command Strip requires notification permission", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showWallpaperDialog) {
        WallpaperConfigDialog(
            onDismiss = { showWallpaperDialog = false },
            onConfirm = {
                showWallpaperDialog = false
                try {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, MoriWallpaperService::class.java)
                        )
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Live wallpaper not supported", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showNewGoalDialog) {
        NewGoalDialog(
            onDismiss = { showNewGoalDialog = false },
            onDeploy = { content, days ->
                onDeployMission(content, days)
                showNewGoalDialog = false
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewGoalDialog = true },
                containerColor = CommandGold,
                contentColor = OnyxBlack,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Goal")
            }
        },
        containerColor = OnyxBlack
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            DeploymentScreenContent(
                activeMissions = activeMissions,
                onCompleteMission = onCompleteMission,
                onSetWallpaper = { showWallpaperDialog = true }
            )
        }
    }
}

@Composable
fun NewGoalDialog(onDismiss: () -> Unit, onDeploy: (String, Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "INITIALIZE NEW MISSION", 
                color = CommandGold, 
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 16.sp
            ) 
        },
        containerColor = OnyxBlack,
        modifier = Modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(GhostSlateBorder, CommandGold.copy(alpha = 0.25f))
            ),
            shape = CutCornerShape(12.dp)
        ),
        shape = CutCornerShape(12.dp),
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 40) text = it },
                    label = { Text("MISSION OBJECTIVE", color = CommandGoldMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextWhite, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    shape = CutCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CommandGold,
                        unfocusedBorderColor = GhostSlateBorder,
                        focusedLabelColor = CommandGold,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = CommandGold
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = days,
                    onValueChange = { if (it.all { char -> char.isDigit() }) days = it },
                    label = { Text("TARGET DURATION (DAYS)", color = CommandGoldMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    placeholder = { Text("e.g. 30", color = CommandGoldMuted.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextWhite, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    shape = CutCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CommandGold,
                        unfocusedBorderColor = GhostSlateBorder,
                        focusedLabelColor = CommandGold,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = CommandGold
                    )
                )
            }
        },
        confirmButton = {
            val targetDays = days.toIntOrNull()
            Button(
                onClick = { 
                    if (text.isNotBlank() && targetDays != null) onDeploy(text, targetDays) 
                },
                enabled = text.isNotBlank() && targetDays != null && targetDays > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CommandGold,
                    contentColor = OnyxBlack,
                    disabledContainerColor = GhostSlateBorder,
                    disabledContentColor = TextMuted
                ),
                shape = CutCornerShape(4.dp)
            ) {
                Text("DEPLOY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun DeploymentScreen(viewModel: MainViewModel, onSetWallpaper: () -> Unit) {
    val activeMissions by viewModel.activeMissions.collectAsState()
    DeploymentScreenContent(
        activeMissions = activeMissions,
        onCompleteMission = { viewModel.completeMission(it) },
        onSetWallpaper = onSetWallpaper
    )
}

@Composable
fun DeploymentScreenContent(
    activeMissions: List<MissionEntity>,
    onCompleteMission: (MissionEntity) -> Unit,
    onSetWallpaper: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MoriWallpaperService.PREFS_NAME, Context.MODE_PRIVATE) }
    val mode = prefs.getString(MoriWallpaperService.KEY_MODE, MoriWallpaperService.MODE_YEARLY) ?: MoriWallpaperService.MODE_YEARLY
    val calendar = Calendar.getInstance()
    
    val (currentDay, totalDays, label) = remember(mode) {
        when (mode) {
            MoriWallpaperService.MODE_WEEKLY -> {
                val cur = calendar.get(Calendar.WEEK_OF_YEAR)
                Triple(cur, 52, "WEEK")
            }
            MoriWallpaperService.MODE_CUSTOM -> {
                val total = prefs.getInt(MoriWallpaperService.KEY_CUSTOM_TOTAL, 365)
                val start = prefs.getLong(MoriWallpaperService.KEY_START_DATE_MS, System.currentTimeMillis())
                val cur = ((System.currentTimeMillis() - start) / (1000 * 60 * 60 * 24)).toInt() + 1
                Triple(cur, total, "DAY")
            }
            else -> { // Yearly
                val cur = calendar.get(Calendar.DAY_OF_YEAR)
                val total = calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
                Triple(cur, total, "DAY")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COMMANDER V1.0", color = CommandGold.copy(alpha = 0.2f), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            TextButton(onClick = onSetWallpaper) {
                Text("SETUP COMMAND", color = CommandGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        GoalClockArc(current = currentDay, total = totalDays)

        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "ACTIVE MISSIONS",
            color = CommandGold,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(activeMissions) { mission ->
                MissionGridCard(mission, onComplete = { onCompleteMission(mission) })
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val elapsedPercent = if (totalDays > 0) ((currentDay.toFloat() / totalDays.toFloat()) * 100).toInt() else 0
            StatCard("$elapsedPercent%", "ELAPSED", Modifier.weight(1f))
            StatCard("${(totalDays - currentDay).coerceAtLeast(0)}", "$label LEFT", Modifier.weight(1f))
            StatCard("0", "STREAK", Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun MissionGridCard(mission: MissionEntity, onComplete: () -> Unit) {
    val elapsedMillis = System.currentTimeMillis() - mission.timestamp
    val durationMillis = mission.targetDays.toLong() * 24 * 60 * 60 * 1000L
    val progress = if (durationMillis > 0) (elapsedMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f) else 1f
    val daysLeft = (mission.targetDays - (elapsedMillis / (1000 * 60 * 60 * 24))).coerceAtLeast(0)
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "CardPressScale"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "CardProgressAnimation"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(GhostSlateBorder, CommandGold.copy(alpha = 0.25f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GhostSlate.copy(alpha = 0.8f), OnyxBlack.copy(alpha = 0.5f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(1.dp, CommandGold.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .background(CommandGoldGlow, RoundedCornerShape(6.dp))
                        .clickable(
                            onClick = onComplete
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete Goal",
                        tint = CommandGold,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mission.content.uppercase(),
                color = TextWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = GhostSlateBorder,
                    strokeWidth = 3.dp,
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = CommandGold,
                    strokeWidth = 3.dp,
                    trackColor = Color.Transparent
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = CommandGold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$daysLeft DAYS LEFT",
                color = CommandGoldMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun GoalClockArc(current: Int, total: Int) {
    val progressTarget = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 1f
    
    // Smooth entry animation for the progress
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "GoalClockProgress"
    )

    // Breathing glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "GlowBreathing")
    val breathingGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingAlpha"
    )

    val percentage = (progressTarget * 100).toInt()

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthBg = 2.dp.toPx()
            val strokeWidthFg = 5.dp.toPx()
            val glowWidth = 10.dp.toPx()

            // 1. Dash-grid background arc
            val dashPattern = floatArrayOf(4.dp.toPx(), 6.dp.toPx())
            drawArc(
                color = CommandGold.copy(alpha = 0.08f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidthBg, 
                    pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
                )
            )

            // 2. Glowing back-shadow under progress (breathing aura)
            drawArc(
                color = CommandGold.copy(alpha = breathingGlowAlpha * 0.12f),
                startAngle = 140f,
                sweepAngle = 260f * animatedProgress,
                useCenter = false,
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )

            // 3. Main active progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to CommandGold.copy(alpha = 0.7f),
                    0.5f to CommandGold,
                    1.0f to CommandGold
                ),
                startAngle = 140f,
                sweepAngle = 260f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidthFg, cap = StrokeCap.Round)
            )
            
            // 4. Indicator dot at the leading edge of progress
            if (animatedProgress > 0f) {
                val angleRad = Math.toRadians((140f + 260f * animatedProgress).toDouble())
                val radius = size.width / 2f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val dotX = centerX + radius * Math.cos(angleRad).toFloat()
                val dotY = centerY + radius * Math.sin(angleRad).toFloat()
                
                // Outer glowing aura
                drawCircle(
                    color = CommandGold.copy(alpha = breathingGlowAlpha),
                    radius = 8.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
                // Inner dot
                drawCircle(
                    color = CommandGold,
                    radius = 4.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            modifier = Modifier.offset(y = 10.dp)
        ) {
            Text(
                text = "DAY $current".uppercase(), 
                color = TextWhite, 
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace, 
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$percentage% ELAPSED", 
                color = CommandGold, 
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SYSTEM ONLINE", 
                color = TextMuted, 
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun WallpaperConfigDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(MoriWallpaperService.PREFS_NAME, Context.MODE_PRIVATE)
    
    var selectedMode by remember { mutableStateOf(prefs.getString(MoriWallpaperService.KEY_MODE, MoriWallpaperService.MODE_YEARLY) ?: MoriWallpaperService.MODE_YEARLY) }
    var customTotal by remember { mutableStateOf(prefs.getInt(MoriWallpaperService.KEY_CUSTOM_TOTAL, 100).toString()) }
    var lockOnly by remember { mutableStateOf(prefs.getBoolean(MoriWallpaperService.KEY_LOCK_ONLY, false)) }
    var aodEnabled by remember { mutableStateOf(prefs.getBoolean(MoriWallpaperService.KEY_AOD_ENABLED, false)) }
    var homeImageUri by remember { mutableStateOf(prefs.getString(MoriWallpaperService.KEY_HOME_IMAGE_URI, null)) }
    var editingImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            editingImageUri = it
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "WALLPAPER CONFIG", 
                color = CommandGold, 
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 16.sp
            ) 
        },
        containerColor = OnyxBlack,
        modifier = Modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(GhostSlateBorder, CommandGold.copy(alpha = 0.25f))
            ),
            shape = CutCornerShape(12.dp)
        ),
        shape = CutCornerShape(12.dp),
        text = {
            Column {
                listOf(
                    MoriWallpaperService.MODE_YEARLY to "YEARLY (365 DAYS)",
                    MoriWallpaperService.MODE_WEEKLY to "WEEKLY (52 WEEKS)",
                    MoriWallpaperService.MODE_CUSTOM to "CUSTOM RANGE"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedMode == mode),
                                onClick = { selectedMode = mode }
                             )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedMode == mode),
                            onClick = { selectedMode = mode },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CommandGold,
                                unselectedColor = TextMuted
                            )
                        )
                        Text(
                            text = label,
                            color = if (selectedMode == mode) CommandGold else TextMuted,
                            modifier = Modifier.padding(start = 8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }

                if (selectedMode == MoriWallpaperService.MODE_CUSTOM) {
                    OutlinedTextField(
                        value = customTotal,
                        onValueChange = { if (it.all { char -> char.isDigit() }) customTotal = it },
                        label = { Text("TOTAL DAYS", color = CommandGoldMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CommandGold,
                            unfocusedBorderColor = GhostSlateBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = CommandGold
                        ),
                        shape = CutCornerShape(4.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GhostSlateBorder,
                        contentColor = TextWhite
                    ),
                    shape = CutCornerShape(4.dp)
                ) {
                    Text(
                        text = if (homeImageUri == null) "SELECT HOME PHOTO" else "CHANGE PHOTO",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ALWAYS ON DISPLAY", color = TextWhite, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    Checkbox(
                        checked = aodEnabled,
                        onCheckedChange = { aodEnabled = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CommandGold,
                            uncheckedColor = TextMuted
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LOCK SCREEN ONLY", color = TextWhite, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    Switch(
                        checked = lockOnly,
                        onCheckedChange = { lockOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CommandGold,
                            checkedTrackColor = CommandGoldGlow,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = GhostSlateBorder
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.edit().apply {
                        putString(MoriWallpaperService.KEY_MODE, selectedMode)
                        putInt(MoriWallpaperService.KEY_CUSTOM_TOTAL, customTotal.toIntOrNull() ?: 100)
                        putLong(MoriWallpaperService.KEY_START_DATE_MS, System.currentTimeMillis())
                        putBoolean(MoriWallpaperService.KEY_LOCK_ONLY, lockOnly)
                        putBoolean(MoriWallpaperService.KEY_AOD_ENABLED, aodEnabled)
                        putString(MoriWallpaperService.KEY_HOME_IMAGE_URI, homeImageUri)
                        commit() // Use commit() instead of apply() to ensure synchronous write
                    }
                    // Send explicit broadcast to force wallpaper refresh
                    context.sendBroadcast(Intent(MoriWallpaperService.ACTION_REFRESH).setPackage(context.packageName))
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CommandGold,
                    contentColor = OnyxBlack
                ),
                shape = CutCornerShape(4.dp)
            ) {
                Text("DEPLOY GRID", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    )

    if (editingImageUri != null) {
        PhotoEditorOverlay(
            uri = editingImageUri!!,
            onDismiss = { editingImageUri = null },
            onConfirm = { processedUri ->
                homeImageUri = processedUri.toString()
                editingImageUri = null
            }
        )
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(GhostSlateBorder, GhostSlateBorder.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GhostSlate.copy(alpha = 0.6f), OnyxBlack.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value, 
            color = CommandGold, 
            fontSize = 18.sp, 
            fontWeight = FontWeight.Bold, 
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(), 
            color = TextMuted, 
            fontSize = 9.sp, 
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

enum class TintMode { NONE, GOLD, SLATE }

@Composable
fun PhotoEditorOverlay(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    LaunchedEffect(uri) {
        coroutineScope.launch(Dispatchers.IO) {
            bitmap = loadScaledBitmapFromUri(context, uri)
            isLoading = false
        }
    }
    
    var zoom by remember { mutableStateOf(1.0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var dimming by remember { mutableStateOf(0.4f) }
    var tintMode by remember { mutableStateOf(TintMode.NONE) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OnyxBlack)
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CommandGold
                )
            } else if (bitmap == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("FAILED TO LOAD IMAGE", color = BloodRed, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss, shape = CutCornerShape(4.dp)) {
                        Text("BACK", fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                val nonNullBitmap = bitmap!!
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "FRAME BACKGROUND",
                            color = CommandGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        TextButton(onClick = onDismiss) {
                            Text("ABORT", color = TextMuted, fontFamily = FontFamily.Monospace)
                        }
                    }
                    
                    var viewportWidthPx by remember { mutableStateOf(0f) }
                    var viewportHeightPx by remember { mutableStateOf(0f) }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f)
                            .border(1.dp, CommandGold.copy(alpha = 0.3f), CutCornerShape(12.dp))
                            .clip(CutCornerShape(12.dp))
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            viewportWidthPx = size.width
                            viewportHeightPx = size.height
                            
                            clipRect {
                                withTransform({
                                    translate(size.width / 2f + offsetX, size.height / 2f + offsetY)
                                    scale(zoom, zoom, pivot = Offset.Zero)
                                }) {
                                    drawImage(
                                        image = nonNullBitmap.asImageBitmap(),
                                        topLeft = Offset(-nonNullBitmap.width / 2f, -nonNullBitmap.height / 2f)
                                    )
                                }
                            }
                            
                            if (dimming > 0f) {
                                drawRect(
                                    color = Color.Black.copy(alpha = dimming),
                                    size = size
                                )
                            }
                            
                            if (tintMode != TintMode.NONE) {
                                val tintColor = when (tintMode) {
                                    TintMode.GOLD -> CommandGold
                                    TintMode.SLATE -> GhostSlateBorder
                                    else -> Color.Transparent
                                }
                                drawRect(
                                    color = tintColor.copy(alpha = 0.25f),
                                    size = size
                                )
                            }
                            
                            val lineLen = 15.dp.toPx()
                            val strokeW = 1.dp.toPx()
                            
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(0f, 0f), Offset(lineLen, 0f), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, lineLen), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(size.width, 0f), Offset(size.width - lineLen, 0f), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(size.width, 0f), Offset(size.width, lineLen), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(0f, size.height), Offset(lineLen, size.height), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(0f, size.height), Offset(0f, size.height - lineLen), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(size.width, size.height), Offset(size.width - lineLen, size.height), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.6f), Offset(size.width, size.height), Offset(size.width, size.height - lineLen), strokeW)
                            
                            val crossLen = 6.dp.toPx()
                            drawLine(CommandGold.copy(alpha = 0.3f), Offset(size.width/2f - crossLen, size.height/2f), Offset(size.width/2f + crossLen, size.height/2f), strokeW)
                            drawLine(CommandGold.copy(alpha = 0.3f), Offset(size.width/2f, size.height/2f - crossLen), Offset(size.width/2f, size.height/2f + crossLen), strokeW)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ZOOM", color = CommandGoldMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = 1.0f..4.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CommandGold,
                                    activeTrackColor = CommandGold,
                                    inactiveTrackColor = GhostSlateBorder
                                )
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DIM", color = CommandGoldMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                            Slider(
                                value = dimming,
                                onValueChange = { dimming = it },
                                valueRange = 0.0f..0.9f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CommandGold,
                                    activeTrackColor = CommandGold,
                                    inactiveTrackColor = GhostSlateBorder
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("FILTER", color = CommandGoldMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                            
                            listOf(
                                TintMode.NONE to "RAW",
                                TintMode.GOLD to "GOLD HUD",
                                TintMode.SLATE to "OBSIDIAN"
                            ).forEach { (mode, label) ->
                                val isSelected = tintMode == mode
                                Button(
                                    onClick = { tintMode = mode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) CommandGold else GhostSlateBorder,
                                        contentColor = if (isSelected) OnyxBlack else TextWhite
                                    ),
                                    shape = CutCornerShape(4.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val metrics = context.resources.displayMetrics
                                val screenWidth = metrics.widthPixels
                                val screenHeight = metrics.heightPixels
                                
                                val processedBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                                val canvas = AndroidCanvas(processedBitmap)
                                
                                val scaleRatio = if (viewportWidthPx > 0) screenWidth.toFloat() / viewportWidthPx else 1.0f
                                val finalOffsetX = offsetX * scaleRatio
                                val finalOffsetY = offsetY * scaleRatio
                                val finalScale = zoom * scaleRatio
                                
                                val matrix = Matrix()
                                matrix.postTranslate(-nonNullBitmap.width / 2f, -nonNullBitmap.height / 2f)
                                matrix.postScale(finalScale, finalScale)
                                matrix.postTranslate(screenWidth / 2f + finalOffsetX, screenHeight / 2f + finalOffsetY)
                                
                                val paint = AndroidPaint().apply { isAntiAlias = true }
                                canvas.drawBitmap(nonNullBitmap, matrix, paint)
                                
                                if (dimming > 0f) {
                                    val dimPaint = AndroidPaint().apply {
                                        color = AndroidColor.BLACK
                                        alpha = (dimming * 255).toInt().coerceIn(0, 255)
                                        style = AndroidPaint.Style.FILL
                                    }
                                    canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), dimPaint)
                                }
                                
                                if (tintMode != TintMode.NONE) {
                                    val tintColor = when (tintMode) {
                                        TintMode.GOLD -> AndroidColor.parseColor("#E5C158")
                                        TintMode.SLATE -> AndroidColor.parseColor("#26263A")
                                        else -> AndroidColor.TRANSPARENT
                                    }
                                    if (tintColor != AndroidColor.TRANSPARENT) {
                                        val tintPaint = AndroidPaint().apply {
                                            color = tintColor
                                            alpha = (0.25f * 255).toInt()
                                            style = AndroidPaint.Style.FILL
                                        }
                                        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), tintPaint)
                                    }
                                }
                                
                                val outFile = File(context.filesDir, "wallpaper_processed.png")
                                try {
                                    FileOutputStream(outFile).use { out ->
                                        processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    val localUri = Uri.fromFile(outFile).buildUpon()
                                        .appendQueryParameter("t", System.currentTimeMillis().toString())
                                        .build()
                                    launch(Dispatchers.Main) {
                                        onConfirm(localUri)
                                        isSaving = false
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    launch(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to save wallpaper: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        isSaving = false
                                    }
                                } finally {
                                    processedBitmap.recycle()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CommandGold,
                            contentColor = OnyxBlack
                        ),
                        shape = CutCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text("ENGAGE GRID", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            
            if (isSaving) {
                Box(
                    modifier = Modifier.fillMaxSize().background(OnyxBlack.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CommandGold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "TRANSMITTING TELEMETRY DATA...",
                            color = CommandGold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun loadScaledBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        
        var srcWidth = options.outWidth
        var srcHeight = options.outHeight
        var sampleSize = 1
        while (srcWidth > maxDim || srcHeight > maxDim) {
            srcWidth /= 2
            srcHeight /= 2
            sampleSize *= 2
        }
        
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleMissions = listOf(
        MissionEntity(id = 1, content = "Establish Base", timestamp = System.currentTimeMillis() - 172800000, targetDays = 7),
        MissionEntity(id = 2, content = "Resource Collection", timestamp = System.currentTimeMillis(), targetDays = 3)
    )
    LifeLayerTheme {
        MainScreenContent(
            activeMissions = sampleMissions,
            onDeployMission = { _, _ -> },
            onCompleteMission = {}
        )
    }
}
