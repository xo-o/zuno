package com.talkie.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.talkie.ai.auth.AuthViewModel
import com.talkie.ai.reminders.Reminders
import com.talkie.ai.ui.ZunoApp
import com.talkie.ai.ui.theme.ZunoTheme
import com.talkie.ai.viewmodel.VoiceSessionViewModel
import com.talkie.ai.viewmodel.ZunoViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceSessionViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val zunoViewModel: ZunoViewModel by viewModels()

    /** Conversation to open from a reminder notification; cleared once shown. */
    private val openChatRequest = MutableStateFlow<String?>(null)
    private var handledChatIntent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.background)

        // Magic link tokens are single-use, so only redeem on a fresh launch, not on recreation.
        if (savedInstanceState == null) authViewModel.handleMagicLink(intent?.data)
        // A reminder tap can also recreate the activity from saved state (process was killed), so check
        // the intent whenever it hasn't been handled yet.
        handledChatIntent = savedInstanceState?.getString(KEY_HANDLED_CHAT_INTENT)
        handleOpenChat(intent)

        setContent {
            ZunoTheme {
                val permissions = remember {
                    mutableListOf(Manifest.permission.RECORD_AUDIO).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }.toTypedArray()
                }

                var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grantResults ->
                    val audioGranted = grantResults[Manifest.permission.RECORD_AUDIO] == true ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                    val action = pendingVoiceAction
                    pendingVoiceAction = null
                    if (audioGranted) {
                        action?.invoke()
                    }
                }

                fun runWithVoicePermissions(action: () -> Unit) {
                    val missingPermissions = permissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missingPermissions.isEmpty()) {
                        action()
                    } else {
                        pendingVoiceAction = action
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }
                }

                ZunoApp(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    zunoViewModel = zunoViewModel,
                    runWithVoicePermissions = ::runWithVoicePermissions,
                    openChatRequest = openChatRequest,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authViewModel.handleMagicLink(intent.data)
        handledChatIntent = null
        handleOpenChat(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_HANDLED_CHAT_INTENT, handledChatIntent)
    }

    private fun handleOpenChat(intent: Intent?) {
        val conversationId = intent?.getStringExtra(Reminders.EXTRA_CONVERSATION_ID) ?: return
        if (intent.action == handledChatIntent) return
        handledChatIntent = intent.action
        openChatRequest.value = conversationId
    }

    private companion object {
        const val KEY_HANDLED_CHAT_INTENT = "handled_chat_intent"
    }
}
