package com.betesepmu.vendor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.ui.preview.PreviewOverlay
import com.betesepmu.vendor.ui.queue.QueueScreen
import com.betesepmu.vendor.ui.samples.SamplesScreen
import com.betesepmu.vendor.ui.settings.SettingsScreen
import com.betesepmu.vendor.ui.splash.BetEseSplash
import com.betesepmu.vendor.ui.vendor.VendorDashboardScreen
import com.betesepmu.vendor.vendor.VendorViewModel
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Terminal", Icons.Filled.PointOfSale),
    SAMPLES("Samples", Icons.AutoMirrored.Filled.ReceiptLong),
    QUEUE("Queue", Icons.Filled.GridView),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun BetEseAppUi(vm: MainViewModel, vendorVm: VendorViewModel) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1300); showSplash = false }

    Box(Modifier.fillMaxSize()) {
        MainScaffold(vm, vendorVm)
        AnimatedVisibility(visible = showSplash, exit = fadeOut()) { BetEseSplash() }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, vendorVm: VendorViewModel) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val preview by vm.preview.collectAsStateWithLifecycle()

    // Notification permission for the foreground spooler (Android 13+).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vendorVm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> VendorDashboardScreen(vendorVm)
                Tab.SAMPLES -> SamplesScreen(vm)
                Tab.QUEUE -> QueueScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm, vendorVm)
            }
        }
    }

    preview?.let {
        PreviewOverlay(
            ui = it,
            onClose = { vm.closePreview() },
            onPrint = { vm.printFromPreview(); vm.closePreview() },
            onSave = { vm.savePreview(context) },
            onShare = { vm.sharePreview(context) },
        )
    }
}
