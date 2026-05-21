package com.example.lifelayer.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.example.lifelayer.data.AppDatabase
import com.example.lifelayer.data.MissionEntity
import com.example.lifelayer.data.MissionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.math.sqrt

class MoriWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MoriEngine()

    companion object {
        private const val TAG = "MoriWallpaper"
        const val PREFS_NAME = "MoriWallpaperPrefs"
        const val KEY_MODE = "grid_mode"
        const val MODE_YEARLY = "yearly"
        const val MODE_WEEKLY = "weekly"
        const val MODE_CUSTOM = "custom"
        const val KEY_CUSTOM_TOTAL = "custom_total"
        const val KEY_START_DATE_MS = "start_date_ms"
        const val KEY_LOCK_ONLY = "lock_screen_only"
        const val KEY_HOME_IMAGE_URI = "home_image_uri"
        const val KEY_ACHIEVEMENT_TIME = "achievement_time"
        const val KEY_AOD_ENABLED = "aod_enabled"
        const val ACTION_REFRESH = "com.example.lifelayer.REFRESH_WALLPAPER"
    }

    inner class MoriEngine : Engine() {
        private val paint = Paint().apply { isAntiAlias = true }
        private val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var touchX = -1f
        private var touchY = -1f
        private var rippleRadius = 0f
        
        private val scope = CoroutineScope(Dispatchers.IO + Job())
        private var activeMission: MissionEntity? = null
        private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
        private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
        
        private var homeBitmap: Bitmap? = null
        private var lastLoadedUri: String? = null
        private var lastLoadedTimestamp: Long = 0L

        private val drawRunnable = Runnable { draw() }

        private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            Log.d(TAG, "Preference changed: $key")
            if (key == KEY_HOME_IMAGE_URI) {
                // Force-invalidate the bitmap cache so we reload from disk
                invalidateBitmapCache()
                // Delay draw slightly to ensure file writes are flushed
                handler.postDelayed({ draw() }, 150)
            } else if (key == KEY_MODE || key == KEY_LOCK_ONLY || key == KEY_AOD_ENABLED) {
                draw()
            }
        }

        private val refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Refresh broadcast received")
                invalidateBitmapCache()
                handler.postDelayed({ draw() }, 150)
            }
        }

        private fun invalidateBitmapCache() {
            Log.d(TAG, "Invalidating bitmap cache. Old URI: $lastLoadedUri")
            homeBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            homeBitmap = null
            lastLoadedUri = null
            lastLoadedTimestamp = 0L
        }

        init {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            val filter = IntentFilter(ACTION_REFRESH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(refreshReceiver, filter)
            }
            Log.d(TAG, "MoriEngine initialized, listeners registered")
        }

        override fun onDestroy() {
            super.onDestroy()
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
            handler.removeCallbacks(drawRunnable)
            Log.d(TAG, "MoriEngine destroyed")
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            // Optimization: Only keep drawing if visible OR if AOD is enabled and we are on lockscreen
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val aodEnabled = prefs.getBoolean(KEY_AOD_ENABLED, false)
            
            if (visible || (aodEnabled && keyguardManager.isKeyguardLocked)) {
                checkMissionStatus()
                draw()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
            homeBitmap?.recycle()
            homeBitmap = null
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                touchX = event.x
                touchY = event.y
                rippleRadius = 0f
                draw()
            }
            super.onTouchEvent(event)
        }

        private fun checkMissionStatus() {
            scope.launch {
                val db = AppDatabase.getDatabase(this@MoriWallpaperService)
                val repo = MissionRepository(db.missionDao())
                activeMission = repo.getActiveMission()
            }
        }

        private fun loadHomeBitmap(uriString: String?, width: Int, height: Int) {
            if (uriString == null) return
            try {
                val uri = Uri.parse(uriString)
                // Strip query parameters to get the actual file path
                val cleanPath = uri.path
                var fileLastModified = 0L
                if (uri.scheme == "file" && cleanPath != null) {
                    val file = File(cleanPath)
                    if (file.exists()) {
                        fileLastModified = file.lastModified()
                    } else {
                        Log.w(TAG, "File does not exist: $cleanPath")
                        return
                    }
                }

                // Cache check: skip reload only if same file AND same modification time AND bitmap is valid
                val baseUri = uri.buildUpon().clearQuery().build().toString()
                if (baseUri == lastLoadedUri && homeBitmap != null && !homeBitmap!!.isRecycled && 
                    (uri.scheme != "file" || fileLastModified == lastLoadedTimestamp)) {
                    Log.d(TAG, "Bitmap cache hit for $baseUri (modified=$fileLastModified, cached=$lastLoadedTimestamp)")
                    return
                }

                Log.d(TAG, "Loading bitmap from: $cleanPath (modified=$fileLastModified, lastCached=$lastLoadedTimestamp)")

                val inputStream = if (uri.scheme == "file" && cleanPath != null) {
                    java.io.FileInputStream(File(cleanPath))
                } else {
                    contentResolver.openInputStream(uri)
                }

                inputStream?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream)
                    if (original == null) {
                        Log.e(TAG, "BitmapFactory.decodeStream returned null for $cleanPath")
                        return
                    }
                    original.let { bitmap ->
                        val scale = Math.max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                        val scaledW = (bitmap.width * scale).toInt()
                        val scaledH = (bitmap.height * scale).toInt()
                        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                        val oldBitmap = homeBitmap
                        val newBitmap = Bitmap.createBitmap(scaled, (scaledW - width) / 2, (scaledH - height) / 2, width, height)
                        homeBitmap = newBitmap
                        
                        if (scaled != newBitmap && scaled != bitmap) {
                            scaled.recycle()
                        }
                        if (bitmap != newBitmap && bitmap != scaled) {
                            bitmap.recycle()
                        }
                        if (oldBitmap != null && oldBitmap != newBitmap && !oldBitmap.isRecycled) {
                            oldBitmap.recycle()
                        }
                        
                        lastLoadedUri = baseUri
                        lastLoadedTimestamp = fileLastModified
                        Log.d(TAG, "Bitmap loaded successfully: ${newBitmap.width}x${newBitmap.height}")
                    }
                } ?: Log.e(TAG, "Could not open input stream for $cleanPath")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading home bitmap", e)
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lockOnly = prefs.getBoolean(KEY_LOCK_ONLY, false)
                    val aodEnabled = prefs.getBoolean(KEY_AOD_ENABLED, false)
                    val imageUri = prefs.getString(KEY_HOME_IMAGE_URI, null)
                    val achievementTime = prefs.getLong(KEY_ACHIEVEMENT_TIME, 0L)
                    
                    val isLocked = keyguardManager.isKeyguardLocked
                    val isInteractive = powerManager.isInteractive
                    
                    // AOD state
                    val isAmbient = aodEnabled && isLocked && !isInteractive
                    
                    val now = System.currentTimeMillis()
                    val showingAchievement = now - achievementTime < 5000L

                    if (showingAchievement) {
                        drawAchievement(canvas, now - achievementTime)
                    } else {
                        val shouldShowContent = !lockOnly || isLocked || isPreview

                        if (!isLocked && imageUri != null) {
                            loadHomeBitmap(imageUri, canvas.width, canvas.height)
                            val bitmap = homeBitmap
                            if (bitmap != null && !bitmap.isRecycled) {
                                canvas.drawBitmap(bitmap, 0f, 0f, null)
                            } else {
                                canvas.drawColor(Color.BLACK)
                            }
                        } else {
                            canvas.drawColor(Color.BLACK)
                        }

                        if (shouldShowContent) {
                            drawMonolithDesignLayered(canvas, prefs, isAmbient)
                        }
                    }

                    if (visible || isAmbient) {
                        // If in AOD, we stop updating once a static frame is drawn
                        if (!isAmbient) {
                            val delay = if (showingAchievement || touchX != -1f) 16L else 1000L
                            handler.postDelayed(drawRunnable, delay)
                        }
                    }
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawAchievement(canvas: Canvas, elapsed: Long) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            canvas.drawColor(Color.BLACK)

            val alpha = (255 * (1f - elapsed / 5000f)).toInt().coerceIn(0, 255)
            val burstPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#D4AF37")
            }

            val random = Random(42)
            for (i in 0 until 100) {
                val angle = random.nextFloat() * 360f
                val length = (elapsed / 5000f) * width * 2f
                val startR = random.nextFloat() * 200f
                val x1 = width / 2 + Math.cos(Math.toRadians(angle.toDouble())).toFloat() * startR
                val y1 = height / 2 + Math.sin(Math.toRadians(angle.toDouble())).toFloat() * startR
                val x2 = width / 2 + Math.cos(Math.toRadians(angle.toDouble())).toFloat() * (startR + length)
                val y2 = height / 2 + Math.sin(Math.toRadians(angle.toDouble())).toFloat() * (startR + length)
                burstPaint.alpha = (alpha * (1f - (startR + length) / (width * 2f))).toInt().coerceIn(0, 255)
                canvas.drawLine(x1, y1, x2, y2, burstPaint)
            }

            textPaint.alpha = alpha
            textPaint.color = Color.parseColor("#D4AF37")
            textPaint.textSize = 80f
            textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            canvas.drawText("MISSION", width / 2, height / 2 - 100f, textPaint)
            canvas.drawText("ACCOMPLISHED.", width / 2, height / 2, textPaint)
            
            textPaint.textSize = 40f
            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.MONOSPACE
            canvas.drawText("THE LINE ADVANCED.", width / 2, height / 2 + 100f, textPaint)
        }

        private fun drawMonolithDesignLayered(canvas: Canvas, prefs: android.content.SharedPreferences, isAmbient: Boolean) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            
            val mission = activeMission
            val remainingMillis = mission?.let { (it.timestamp + it.durationMillis) - System.currentTimeMillis() } ?: 0L
            val isCritical = mission != null && remainingMillis < 3600000L // 1 hour

            val primaryColor = if (isCritical) Color.parseColor("#FF3B30") else Color.parseColor("#D4AF37")
            val baseColor = if (isAmbient) primaryColor.withAlpha(80) else primaryColor

            // 1. Draw Arc at the top
            val arcPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = if (isAmbient) 2f else 12f
                strokeCap = Paint.Cap.ROUND
                color = baseColor
            }
            val arcTopY = height * 0.12f
            val arcBottomY = height * 0.38f
            val arcRect = RectF(width / 2 - 350, arcTopY, width / 2 + 350, arcBottomY)
            
            arcPaint.alpha = if (isAmbient) 15 else 40
            canvas.drawArc(arcRect, 180f, 180f, false, arcPaint)
            
            mission?.let {
                val elapsed = System.currentTimeMillis() - it.timestamp
                val progress = (elapsed.toFloat() / it.durationMillis).coerceIn(0f, 1f)
                arcPaint.alpha = if (isAmbient) 80 else 255
                canvas.drawArc(arcRect, 180f, 180f * progress, false, arcPaint)
            }

            // LAYER 1: BOTTOM - Mori Grid (Dots) starting below the arc
            val gridStartY = arcBottomY + 50f
            drawDotsFullBackground(canvas, prefs, isAmbient, primaryColor, gridStartY)

            // LAYER 2: TOP - Goal Card sitting ON TOP of dots with semi-transparent background
            val boxBgPaint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK.withAlpha(if (isAmbient) 100 else 180)
                style = Paint.Style.FILL
            }
            val boxPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = if (isAmbient) 1f else 2f
                color = baseColor
            }
            
            val boxWidth = width * 0.85f
            val boxHeight = 280f
            val boxY = height * 0.42f
            val boxRect = RectF((width - boxWidth) / 2, boxY, (width + boxWidth) / 2, boxY + boxHeight)
            
            canvas.drawRoundRect(boxRect, 12f, 12f, boxBgPaint)
            boxPaint.alpha = if (isAmbient) 40 else 255
            canvas.drawRoundRect(boxRect, 12f, 12f, boxPaint)

            // Objective Text in Box
            val textCenterY = boxRect.centerY()
            textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textPaint.textSize = 38f
            textPaint.color = baseColor
            textPaint.alpha = if (isAmbient) 100 else 255
            
            val weekText = "WEEK 1,452"
            canvas.drawText(weekText, width / 2, textCenterY - 50f, textPaint)
            
            textPaint.textSize = 32f
            textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            val objectiveText = mission?.let { "OBJECTIVE: ${it.content.uppercase()}" } ?: "MEMENTO MORI"
            canvas.drawText(objectiveText, width / 2, textCenterY + 10f, textPaint)
            
            mission?.let {
                val hours = (remainingMillis / (1000 * 60 * 60))
                val minutes = (remainingMillis / (1000 * 60)) % 60
                val seconds = (remainingMillis / 1000) % 60
                val timeStrRem = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d REMAINING", hours, minutes, seconds)
                canvas.drawText(timeStrRem, width / 2, textCenterY + 70f, textPaint)
            }
        }

        private fun drawDotsFullBackground(canvas: Canvas, prefs: android.content.SharedPreferences, isAmbient: Boolean, primaryColor: Int, startY: Float) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            
            val mode = prefs.getString(KEY_MODE, MODE_YEARLY) ?: MODE_YEARLY
            val calendar = Calendar.getInstance()
            val totalDots: Int
            val currentDotIndex: Int
            val columns: Int

            when (mode) {
                MODE_WEEKLY -> {
                    totalDots = 52
                    currentDotIndex = calendar.get(Calendar.WEEK_OF_YEAR) - 1
                    columns = 4
                }
                MODE_CUSTOM -> {
                    totalDots = prefs.getInt(KEY_CUSTOM_TOTAL, 100)
                    val startDateMs = prefs.getLong(KEY_START_DATE_MS, System.currentTimeMillis())
                    val diffMs = System.currentTimeMillis() - startDateMs
                    currentDotIndex = (diffMs / (1000 * 60 * 60 * 24)).toInt()
                    columns = 10
                }
                else -> { // MODE_YEARLY
                    totalDots = calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
                    currentDotIndex = calendar.get(Calendar.DAY_OF_YEAR) - 1
                    columns = 15
                }
            }

            val rows = (totalDots + columns - 1) / columns
            val spacingX = width / (columns + 2)
            
            val maxAvailableHeight = height - startY - 100f
            val spacingY = (maxAvailableHeight / rows).coerceAtMost(spacingX)
            
            val dotRadius = spacingY * 0.35f
            val gridWidth = (columns - 1) * spacingX
            val startX = (width - gridWidth) / 2

            for (i in 0 until totalDots) {
                val r = i / columns
                val c = i % columns
                val x = startX + (c * spacingX)
                val y = startY + (r * spacingY)

                var currentDotRadius = dotRadius
                
                if (!isAmbient && touchX != -1f) {
                    val dist = sqrt((x - touchX) * (x - touchX) + (y - touchY) * (y - touchY))
                    if (dist < rippleRadius && dist > rippleRadius - 50f) {
                        currentDotRadius *= 2.0f
                    }
                }

                when {
                    i == currentDotIndex -> {
                        paint.color = if (activeMission != null) primaryColor else Color.parseColor("#FF3B30")
                        if (isAmbient) paint.alpha = 100
                        if (!isAmbient && System.currentTimeMillis() % 1000 < 500) {
                            currentDotRadius *= 1.3f
                        }
                    }
                    i < currentDotIndex -> {
                        paint.color = Color.WHITE
                        paint.alpha = if (isAmbient) 20 else 80 
                    }
                    else -> {
                        paint.color = Color.parseColor("#222222")
                        paint.alpha = if (isAmbient) 10 else 120
                    }
                }
                canvas.drawCircle(x, y, currentDotRadius, paint)
            }
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (alpha shl 24) or (this and 0x00FFFFFF)
    }
}
