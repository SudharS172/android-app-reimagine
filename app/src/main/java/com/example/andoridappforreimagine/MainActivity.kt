package com.example.andoridappforreimagine

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.andoridappforreimagine.config.AppConfig
import com.example.andoridappforreimagine.service.ScreenCaptureManager
import com.example.andoridappforreimagine.service.UIAutomationService
import com.example.andoridappforreimagine.ui.components.ChatScreen
import com.example.andoridappforreimagine.ui.theme.AndoridAppForReimagineTheme
import com.example.andoridappforreimagine.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private var viewModel: ChatViewModel? = null
    private var errorDialogState by mutableStateOf<ErrorDialogState?>(null)

    private data class ErrorDialogState(
        val title: String,
        val message: String,
        val canRetry: Boolean
    )

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val metrics = resources.displayMetrics
                screenCaptureManager.initialize(
                    result.resultCode,
                    result.data!!,
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi
                )
            } catch (e: Exception) {
                showErrorDialog(
                    "Screen Capture Error",
                    "Failed to initialize screen capture. Please try again or use a different device.",
                    true
                )
            }
        }
    }

    @Composable
    private fun ShowErrorDialog(
        title: String,
        message: String,
        canRetry: Boolean = false,
        onDismiss: () -> Unit,
        onRetry: () -> Unit = {}
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            },
            dismissButton = if (canRetry) {
                {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            } else null
        )
    }

    private fun showErrorDialog(title: String, message: String, canRetry: Boolean = false) {
        errorDialogState = ErrorDialogState(title, message, canRetry)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        try {
            screenCaptureManager = ScreenCaptureManager(this)
        } catch (e: Exception) {
            showErrorDialog(
                "Initialization Error",
                "Failed to initialize the app. Please try again.",
                true
            )
            return
        }

        setContent {
            var showAccessibilityDialog by remember { mutableStateOf(!isAccessibilityServiceEnabled()) }

            val viewModel = viewModel<ChatViewModel>(
                factory = viewModelFactory {
                    initializer {
                        ChatViewModel(
                            context = this@MainActivity,
                            geminiApiKey = AppConfig.GEMINI_API_KEY
                        )
                    }
                }
            )
            this.viewModel = viewModel

            val messages by viewModel.messages.collectAsState()
            val inputText by viewModel.inputText.collectAsState()
            val isProcessing by viewModel.isProcessing.collectAsState()

            LaunchedEffect(Unit) {
                requestScreenCapturePermission()
            }

            AndoridAppForReimagineTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        errorDialogState?.let { state ->
                            ShowErrorDialog(
                                title = state.title,
                                message = state.message,
                                canRetry = state.canRetry,
                                onDismiss = { errorDialogState = null },
                                onRetry = {
                                    errorDialogState = null
                                    requestScreenCapturePermission()
                                }
                            )
                        }

                        if (showAccessibilityDialog) {
                            AlertDialog(
                                onDismissRequest = { showAccessibilityDialog = false },
                                title = { Text("Accessibility Permission Required") },
                                text = { Text("This app requires accessibility service to function properly. Please enable it in settings.") },
                                confirmButton = {
                                    Button(onClick = {
                                        openAccessibilitySettings()
                                        showAccessibilityDialog = false
                                    }) {
                                        Text("Open Settings")
                                    }
                                }
                            )
                        }

                        ChatScreen(
                            messages = messages,
                            inputText = inputText,
                            isProcessing = isProcessing,
                            onTextChange = viewModel::onInputTextChanged,
                            onSendClick = viewModel::sendMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled()) {
            UIAutomationService.getInstance()?.let { service ->
                viewModel?.setAutomationService(service)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager.cleanup()
    }

    private fun requestScreenCapturePermission() {
        val intent = screenCaptureManager.getMediaProjectionIntent()
        mediaProjectionLauncher.launch(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { it.id.contains(packageName) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
