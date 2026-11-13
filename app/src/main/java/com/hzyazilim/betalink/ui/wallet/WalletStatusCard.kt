package com.hzyazilim.betalink.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hzyazilim.betalink.core.data.BetaWallet
import com.hzyazilim.betalink.core.data.CampaignRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WalletStatusCard() {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    var wallet by remember { mutableStateOf<BetaWallet?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { repository.walletSnapshot() }
        wallet = result.first
        error = result.second
        loading = false
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BETA PUAN BAKİYESİ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text((wallet?.balance?.toString() ?: if (loading) "…" else "0") + " BP", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((wallet?.completedSupportTests?.toString() ?: if (loading) "…" else "0"), style = MaterialTheme.typography.titleMedium)
                    Text("Tamamlanan test", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
        }
    }
}