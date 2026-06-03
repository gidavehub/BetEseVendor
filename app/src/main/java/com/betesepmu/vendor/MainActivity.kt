package com.betesepmu.vendor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.betesepmu.vendor.ui.BetEseAppUi
import com.betesepmu.vendor.ui.MainViewModel
import com.betesepmu.vendor.ui.auth.LoginScreen
import com.betesepmu.vendor.ui.theme.BetEseVendorTheme
import com.betesepmu.vendor.vendor.VendorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetEseVendorTheme {
                val vendorVm: VendorViewModel = viewModel()
                val user by vendorVm.currentUser.collectAsStateWithLifecycle()
                if (user == null) {
                    LoginScreen(vendorVm)
                } else {
                    val vm: MainViewModel = viewModel()
                    BetEseAppUi(vm, vendorVm)
                }
            }
        }
    }
}
