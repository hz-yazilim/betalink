package com.hzyazilim.betalink.ui.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hzyazilim.betalink.core.data.Campaign
import com.hzyazilim.betalink.core.data.CampaignRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CampaignBrowserScreen(myTests: Boolean) {
    val context = LocalContext.current
    val repository = remember { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf<List<Campaign>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(myTests, refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            if (myTests) repository.myCampaigns() else repository.listOpenCampaigns()
        }
        campaigns = result.first
        message = result.second
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text(if (myTests) "TESTLERİM" else "KEŞFET", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Text(if (myTests) "Katıldığın kampanyalar" else "Açık test kampanyaları", style = MaterialTheme.typography.headlineSmall)
        if (myTests) {
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("＋ Geliştirici kampanyası oluştur")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else if (campaigns.isEmpty()) {
            Text(
                if (myTests) "Henüz katıldığın kampanya yok. İstersen geliştirici olarak ilk kampanyanı oluşturabilirsin."
                else "Şu anda açık kampanya bulunmuyor. Daha sonra tekrar kontrol et.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                items(campaigns, key = { it.id }) { campaign ->
                    CampaignCard(
                        campaign = campaign,
                        myTests = myTests,
                        onApply = {
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { repository.applyToCampaign(campaign.id) }
                                message = error ?: "Başvurun alındı. Kampanya sahibinin onayını bekle."
                                if (error == null) refresh++
                            }
                        },
                        onConfirmOptIn = {
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { repository.confirmPlayOptIn(campaign.id) }
                                message = error ?: "Play testine katıldığını beyan ettin. Bu beyan Google Play Console tarafından doğrulanmış değildir."
                                if (error == null) refresh++
                            }
                        },
                        onCheckIn = {
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { repository.checkInToday(campaign.id) }
                                message = error ?: "Bugünkü test onayın kaydedildi."
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateCampaignDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, url, description, category, target, minAndroid ->
                scope.launch {
                    val error = withContext(Dispatchers.IO) {
                        repository.createCampaign(name, url, description, category, target, minAndroid)
                    }
                    message = error ?: "Kampanya oluşturuldu ve yayına alındı."
                    if (error == null) {
                        showCreate = false
                        refresh++
                    }
                }
            }
        )
    }
}

@Composable
private fun CampaignCard(campaign: Campaign, myTests: Boolean, onApply: () -> Unit, onConfirmOptIn: () -> Unit, onCheckIn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(campaign.category.uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                Text("${campaign.targetTesters} tester hedefi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(campaign.appName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 7.dp))
            if (campaign.description.isNotBlank()) {
                Text(campaign.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                campaign.minimumAndroid?.let { "Minimum Android $it" } ?: "Android sürüm kısıtlaması yok",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (!myTests) {
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Test için başvur")
                }
            } else {
                Text("Başvuru durumu: ${campaign.membershipStatus ?: "bilinmiyor"}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                if (campaign.membershipStatus == "accepted") {
                    Button(onClick = onConfirmOptIn, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Play testine katıldım")
                    }
                }
                if (campaign.membershipStatus in listOf("opted_in", "active")) {
                    Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Bugünkü test onayını ver")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCampaignDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, Int, Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var target by remember { mutableStateOf("12") }
    var android by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni test kampanyası") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Uygulama adı") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Google Play kapalı test katılım bağlantısı") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Açıklama ve test beklentileri") }, minLines = 2)
                OutlinedTextField(category, { category = it }, label = { Text("Kategori (ör. productivity)") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Tester hedefi (12 önerilir)") }, singleLine = true)
                OutlinedTextField(android, { android = it }, label = { Text("Minimum Android (isteğe bağlı)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val count = target.toIntOrNull()
                val min = android.takeIf { it.isNotBlank() }?.toIntOrNull()
                when {
                    name.trim().length < 2 -> error = "Uygulama adını gir."
                    !url.trim().startsWith("https://") -> error = "Google Play bağlantısı https:// ile başlamalı."
                    count == null || count !in 12..1000 -> error = "Kapalı test hedefi en az 12, en fazla 1000 tester olmalı."
                    android.isNotBlank() && (min == null || min < 1) -> error = "Android sürümünü sayı olarak gir."
                    else -> onCreate(name, url, description, category.ifBlank { "other" }, count, min)
                }
            }) { Text("Yayınla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
