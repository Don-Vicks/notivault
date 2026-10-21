package com.notivault

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.notivault.capture.NotiListenerService
import com.notivault.capture.RecoveredNotifier
import com.notivault.ui.MainViewModel
import com.notivault.ui.VaultApp
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity (not ComponentActivity) so BiometricPrompt can attach. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the vault out of screenshots and the recents thumbnail (debug builds allow captures for QA).
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { VaultApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshListener(NotiListenerService.isEnabled(this))
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getLongExtra(RecoveredNotifier.EXTRA_MESSAGE_ID, -1L)?.takeIf { it > 0 }?.let(vm::openMessage)
    }
}
