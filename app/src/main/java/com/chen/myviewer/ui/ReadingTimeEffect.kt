package com.chen.myviewer.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.chen.myviewer.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private class ReadingScreen(var on: Boolean)

@Composable
internal fun ReadingTimeEffect(vm: MainViewModel, token: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    val screen = remember(token) { ReadingScreen(power?.isInteractive != false) }
    fun refresh() {
        vm.readingVisibility(token, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && screen.on && power?.isInteractive != false && keyguard?.isKeyguardLocked != true)
    }
    DisposableEffect(context, lifecycle, token) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) screen.on = power?.isInteractive != false
            refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                screen.on = intent.action != Intent.ACTION_SCREEN_OFF
                refresh()
            }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        lifecycle.addObserver(observer)
        // These actions are protected system broadcasts; older Android has no export flags.
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        refresh()
        onDispose {
            vm.readingVisibility(token, false)
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    LaunchedEffect(lifecycle, token) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { delay(5000); refresh() }
        }
    }
}
