package com.betesepmu.vendor.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.betesepmu.vendor.ui.components.BrandLogo
import com.betesepmu.vendor.ui.theme.BrandGreenLight

/** Branded in-app splash shown briefly after the system splash, on a white→green wash. */
@Composable
fun BetEseSplash() {
    var started by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (started) 1f else 0.7f, tween(600), label = "scale")
    val alpha by animateFloatAsState(if (started) 1f else 0f, tween(700), label = "alpha")
    LaunchedEffect(Unit) { started = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.White, BrandGreenLight))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BrandLogo(modifier = Modifier.scale(scale).alpha(alpha), size = 150.dp)
            Text(
                "BetEse Vendor",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(alpha),
            )
            Text(
                "On-device thermal print broker",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(alpha).padding(top = 0.dp),
            )
        }
    }
}
