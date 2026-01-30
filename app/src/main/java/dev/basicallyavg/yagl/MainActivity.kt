package dev.basicallyavg.yagl

import android.content.Context
import android.content.Intent
import android.app.WallpaperManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import dev.basicallyavg.yagl.ui.theme.YAGLTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val AtopFont = FontFamily(Font(R.font.atop))

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPrefs = getSharedPreferences("yagl_prefs", Context.MODE_PRIVATE)
        
        enableEdgeToEdge()
        setContent {
            YAGLTheme {
                LauncherScreen(
                    sharedPrefs = sharedPrefs,
                    checkLauncher = { checkDefaultLauncher() },
                    requestPermission = { permission -> requestPermissionLauncher.launch(permission) },
                    openLauncherSettings = { openDefaultLauncherSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isDefaultLauncher = checkDefaultLauncher()
        if (!isDefaultLauncher && !sharedPrefs.getBoolean("launcher_prompt_shown", false)) {
            sharedPrefs.edit().putBoolean("launcher_prompt_shown", true).apply()
            openDefaultLauncherSettings()
        }
    }

    private fun checkDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun openDefaultLauncherSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LauncherScreen(
    sharedPrefs: SharedPreferences,
    checkLauncher: () -> Boolean,
    requestPermission: (String) -> Unit,
    openLauncherSettings: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var wallpaper by remember { mutableStateOf<Bitmap?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showLauncherDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var clockColor by remember {
        mutableStateOf(Color(sharedPrefs.getInt("clock_color", AndroidColor.WHITE)))
    }

    var clockOpacity by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("clock_opacity", 1.0f))
    }

    LaunchedEffect(clockColor) {
        sharedPrefs.edit().putInt("clock_color", clockColor.toArgb()).apply()
    }

    LaunchedEffect(clockOpacity) {
        sharedPrefs.edit().putFloat("clock_opacity", clockOpacity).apply()
    }

    LaunchedEffect(Unit) {
        val isDefaultLauncher = checkLauncher()
        if (!isDefaultLauncher) {
            showLauncherDialog = true
        }

        val missingPermissions = mutableListOf<String>()
        if (context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            showPermissionDialog = true
        }

        apps = withContext(Dispatchers.IO) {
            getInstalledApps(context)
        }
        wallpaper = withContext(Dispatchers.IO) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val drawable = wallpaperManager.getDrawable()
                drawable?.let {
                    when (it) {
                        is BitmapDrawable -> it.bitmap
                        else -> {
                            val width = it.intrinsicWidth.coerceAtLeast(1)
                            val height = it.intrinsicHeight.coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(
                                width,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bitmap)
                            it.setBounds(0, 0, canvas.width, canvas.height)
                            it.draw(canvas)
                            bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
            delay(1000)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(isSearchFocused) {
        if (isSearchFocused) {
            lazyListState.animateScrollToItem(0)
        }
    }

    val filteredApps = apps.filter { 
        it.name.lowercase().contains(searchQuery.lowercase())
    }.sortedBy { it.name.lowercase() }

    val clockAlpha by animateDpAsState(
        targetValue = if (isSearchFocused) 0.dp else 1.dp,
        animationSpec = tween(durationMillis = 300),
        label = "clockAlpha"
    )

    val searchTopPadding by animateDpAsState(
        targetValue = if (isSearchFocused) 100.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "searchPadding"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        wallpaper?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                alpha = 1.0f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                ClockWidget(
                    currentTime = currentTime,
                    currentDate = currentDate,
                    color = clockColor,
                    alpha = clockOpacity,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = clockColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        if (it.isEmpty()) {
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isSearchFocused = focusState.isFocused
                        },
                    placeholder = { 
                        Text(
                            "Search apps...",
                            color = Color.White.copy(alpha = 0.5f)
                        ) 
                    },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isSearchFocused && searchQuery.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No apps found",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppListItem(
                                    app = app,
                                    onClick = { 
                                        launchApp(context, app.packageName)
                                        keyboardController?.hide()
                                        isSearchFocused = false
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!isSearchFocused) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (apps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                AppListItem(
                                    app = app,
                                    onClick = { launchApp(context, app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showLauncherDialog) {
            AlertDialog(
                onDismissRequest = { showLauncherDialog = false },
                title = { Text("Set as Default Launcher") },
                text = { Text("YAGL needs to be set as your default launcher to function properly.") },
                confirmButton = {
                    TextButton(onClick = {
                        openLauncherSettings()
                        showLauncherDialog = false
                    }) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLauncherDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permissions Required") },
                text = { Text("YAGL requires some permissions to function properly. Please grant them when prompted.") },
                confirmButton = {
                    TextButton(onClick = {
                        requestPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                        }
                        showPermissionDialog = false
                    }) {
                        Text("Grant Permissions")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSettingsDialog) {
            Dialog(
                onDismissRequest = { showSettingsDialog = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "YAGL Settings",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Clock Color",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ColorWheel(
                            selectedColor = clockColor,
                            onColorSelected = { clockColor = it },
                            modifier = Modifier.size(200.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Opacity",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Slider(
                            value = clockOpacity,
                            onValueChange = { clockOpacity = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.Gray,
                                thumbColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Opacity: ${(clockOpacity * 100).toInt()}%", color = Color.White)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        androidx.compose.material3.HorizontalDivider(
                            color = Color.White.copy(alpha = 0.2f),
                            thickness = 1.dp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "About YAGL",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "YAGL is fully open-source and under the MIT License.\n\nYou are free to modify, distribute, and use this software for any purpose, including commercial applications.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val githubIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/averageCryptoNerd/YAGL"))
                        Text(
                            text = "GitHub: github.com/averageCryptoNerd/YAGL",
                            fontSize = 14.sp,
                            color = clockColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.clickable {
                                context.startActivity(githubIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClockWidget(
    currentTime: String,
    currentDate: String,
    color: Color = Color.White,
    alpha: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = currentTime,
            fontFamily = AtopFont,
            fontSize = 84.sp,
            color = color.copy(alpha = alpha),
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable {
                try {
                    val clockIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName("com.android.deskclock", "com.android.deskclock.DeskClock")
                    }
                    context.startActivity(clockIntent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent("android.intent.action.SHOW_ALARMS")
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = currentDate,
            fontSize = 18.sp,
            color = color.copy(alpha = alpha * 0.9f),
            fontWeight = FontWeight.Light,
            modifier = Modifier.clickable {
                try {
                    val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("content://com.android.calendar/time/")
                    }
                    context.startActivity(calendarIntent)
                } catch (e: Exception) {
                }
            }
        )
    }
}

@Composable
fun ColorWheel(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAngle by remember { mutableFloatStateOf(0f) }
    
    val gradientColors = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000)
    )
    
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val x = offset.x - centerX
                        val y = offset.y - centerY
                        
                        val distance = kotlin.math.sqrt(x * x + y * y)
                        val maxRadius = size.width / 2
                        
                        if (distance <= maxRadius) {
                            val angle = kotlin.math.atan2(y, x) * (180f / kotlin.math.PI.toFloat())
                            selectedAngle = if (angle < 0) angle + 360f else angle
                            
                            val hue = selectedAngle / 360f
                            val color = android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, 1f, 1f))
                            onColorSelected(Color(color))
                        }
                    }
                }
        ) {
            val radius = size.width / 2
            drawCircle(
                brush = Brush.sweepGradient(gradientColors),
                radius = radius
            )
            
            if (selectedAngle > 0) {
                val angleRad = selectedAngle * (kotlin.math.PI.toFloat() / 180f)
                val indicatorRadius = radius * 0.45f
                val x = size.width / 2 + indicatorRadius * kotlin.math.cos(angleRad)
                val y = size.height / 2 + indicatorRadius * kotlin.math.sin(angleRad)
                
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .background(selectedColor)
        )
    }
}

@Composable
fun AppListItem(app: AppInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                app.icon?.let { drawable ->
                    androidx.compose.foundation.Image(
                        bitmap = drawableToBitmap(drawable).asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}