package com.lumaos.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LumaHomeScreen()
            }
        }
    }
}

@Composable
private fun LumaHomeScreen() {
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF17213B), Color(0xFF090D18), Color(0xFF05070D))
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090D18)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(horizontal = 24.dp, vertical = 34.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TUESDAY, NOVEMBER 10", color = Color(0xFFD5D9E6), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "9:41",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(42.dp))
                Text(
                    "LumaOS",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Your Android. A New Experience.",
                    color = Color(0xFFB7C0D5),
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppPlaceholder("Phone", "☎", Modifier.weight(1f))
                    AppPlaceholder("Messages", "✉", Modifier.weight(1f))
                    AppPlaceholder("Browser", "◎", Modifier.weight(1f))
                    AppPlaceholder("Camera", "◉", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Swipe up to open your apps",
                    color = Color(0xFFB7C0D5),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun AppPlaceholder(label: String, symbol: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    color = Color(0xFF293653),
                    shape = RoundedCornerShape(19.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Color.White, fontSize = 27.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
