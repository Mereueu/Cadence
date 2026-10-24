package com.cadence.player

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.cadence.player.ui.CadenceRoot
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val vm: MusicViewModel by viewModels()

    /** Set when the app is opened from the playback notification; the UI consumes it. */
    private val openPlayerRequest = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            CadenceRoot(vm = vm, openPlayerRequest = openPlayerRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerRequest.value = true
        // "Open with Cadence" from a file manager, browser download, messaging app…
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            vm.playExternal(data)
            openPlayerRequest.value = true
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.cadence.player.OPEN_PLAYER"
    }
}
