package com.example.andoridappforreimagine

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.andoridappforreimagine.config.AppConfig
import com.example.andoridappforreimagine.service.ScreenCaptureManager
import com.example.andoridappforreimagine.service.UIAutomationService
import com.example.andoridappforreimagine.ui.components.ChatScreen
import com.example.andoridappforreimagine.ui.theme.AndoridAppForReimagineTheme
import com.example.andoridappforreimagine.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private var mediaProjectionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val metrics = windowManager.currentWindowMetrics
                screenCaptureManager.initialize(
                    result.resultCode,
                    data,
                    metrics.bounds.width(),
                    metrics.bounds.height(),
                    resources.displayMetrics.densityDpi
                )
            }
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startScreenCapture()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenCaptureManager = ScreenCaptureManager(this)
        
        setContent {
            AndoridAppForReimagineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showPermissionDialog by remember { mutableStateOf(false) }
                    var showAccessibilityDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        if (!isAccessibilityServiceEnabled()) {
                            showAccessibilityDialog = true
                        } else if (!hasRequiredPermissions()) {
                            showPermissionDialog = true
                        } else {
                            startScreenCapture()
                        }
                    }

                    if (showAccessibilityDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Enable Accessibility Service") },
                            text = { Text("This app requires accessibility service to function. Would you like to enable it now?") },
                            confirmButton = {
                                Button(onClick = {
                                    showAccessibilityDialog = false
                                    openAccessibilitySettings()
                                }) {
                                    Text("Enable")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { finish() }) {
                                    Text("Exit")
                                }
                            }
                        )
                    }

                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Required Permissions") },
                            text = { Text("This app needs additional permissions to function properly.") },
                            confirmButton = {
                                Button(onClick = {
                                    showPermissionDialog = false
                                    requestPermissions()
                                }) {
                                    Text("Grant Permissions")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { finish() }) {
                                    Text("Exit")
                                }
                            }
                        )
                    }

                    val viewModel = viewModel<ChatViewModel>(
                        factory = ViewModelFactory(
                            context = applicationContext,
                            geminiApiKey = AppConfig.GEMINI_API_KEY
                        )
                    )

                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun startScreenCapture() {
        mediaProjectionResultLauncher.launch(screenCaptureManager.getMediaProjectionIntent())
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true // No dangerous permissions needed for Android 13+
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        } else {
            startScreenCapture()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any { it.id.contains(packageName) }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled()) {
            UIAutomationService.getInstance()?.let { service ->
                // Update ViewModel with automation service
                val viewModel = (application as? ViewModelProvider.Factory)?.let {
                    ViewModelProvider(this, it)[ChatViewModel::class.java]
                }
                viewModel?.setAutomationService(service)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager.cleanup()
    }
}

class ViewModelFactory(
    private val context: Context,
    private val geminiApiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context, geminiApiKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
