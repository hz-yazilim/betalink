package com.hzyazilim.betalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BetalinkColors = darkColorScheme(
    primary = Color(0xFF8B9CFF),
    secondary = Color(0xFF65D6C4),
    background = Color(0xFF0B1020),
    surface = Color(0xFF151D32),
    onPrimary = Color(0xFF10152B),
    onBackground = Color(0xFFF3F5FF),
    onSurface = Color(0xFFF3F5FF)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = BetalinkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BetalinkHome()
                }
            }
        }
    }
}

@Composable
private fun BetalinkHome() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("BETALINK", color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(10.dp))
            Text("Uygulamalar birlikte
gelişir.", fontSize = 30.sp,
                lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Geliştiricileri gerçek tester topluluklarıyla buluşturan platform.",
                color = Color(0xFFB5BED4), fontSize = 14.sp)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Test topluluğuna hoş geldin", fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Test kampanyalarını keşfet, geliştirici olarak tester bul veya her iki rolde de yer al.",
                        color = Color(0xFFB5BED4), fontSize = 14.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text("Başlayalım")
                    }
                }
            }
        }
        item {
            Text("Betalink neler sunacak?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        item { FeatureRow("12+ tester topluluğu", "Kampanyalar için testerları organize et") }
        item { FeatureRow("14 günlük takip", "Her kampanyanın süresini ayrı izle") }
        item { FeatureRow("Görev ve geri bildirim", "Gerçek kullanım deneyimini kayıt altına al") }
        item { FeatureRow("Gruplar ve yedek liste", "Topluluklarla çalış, ayrılanların yerine davet gönder") }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Bu ilk uygulama iskeletidir. Giriş, kampanyalar ve Supabase bağlantısı sonraki geliştirme adımlarında eklenecek.",
                color = Color(0xFF8994AD), fontSize = 12.sp)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202B48))
        ) {
            Text("✓", Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFFB5BED4), fontSize = 12.sp)
        }
    }
}
