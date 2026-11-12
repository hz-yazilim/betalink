package com.hzyazilim.betalink.ui.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogProperties
import com.hzyazilim.betalink.core.data.Campaign
import com.hzyazilim.betalink.core.data.CampaignRepository
import com.hzyazilim.betalink.ui.wallet.WalletStatusCard
import com.hzyazilim.betalink.core.data.CampaignTask
import com.hzyazilim.betalink.core.data.CampaignTracking
import com.hzyazilim.betalink.core.data.TaskSubmission
import com.hzyazilim.betalink.core.data.FeedbackItem
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
    var selectedTaskCampaign by remember { mutableStateOf<Campaign?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(myTests, refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            if (myTests) repository.myCampaigns()
            else {
                val open = repository.listOpenCampaigns()
                val joined = repository.myCampaigns()
                (joined.first + open.first).distinctBy { it.id } to (open.second ?: joined.second)
            }
        }
        campaigns = result.first
        message = result.second
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(18.dp))
        Text(if (myTests) "TESTLERİM" else "KEŞFET", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Text(if (myTests) "Katıldığın kampanyalar" else "Açık test kampanyaları", style = MaterialTheme.typography.headlineSmall)
        WalletStatusCard()
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Test kampanyası ara") },
            placeholder = { Text("Uygulama, açıklama veya kategori") },
            singleLine = true
        )
        if (myTests) {
            Text("Kampanya oluşturma ücreti 50 BP · Bakiyen 50 BP veya üzerindeyse oluşturabilirsin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("＋ Kampanya oluştur · 50 BP")
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
            val filteredCampaigns = campaigns.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
            if (filteredCampaigns.isEmpty()) {
                Text("Aramana uygun kampanya bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                filteredCampaigns.forEach { campaign ->
                    CampaignCard(
                        campaign = campaign,
                        repository = repository,
                        myTests = campaign.membershipStatus != null,
                        refreshKey = refresh,
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
                        onOpenTasks = { selectedTaskCampaign = campaign },
                        onCheckIn = {
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { repository.checkInToday(campaign.id) }
                                message = error ?: "Bugünkü test onayın kaydedildi."
                                if (error == null) refresh++
                            }
                        }
                    )
                }
            }
        }
    }

    selectedTaskCampaign?.let { campaign ->
        CampaignTaskDialog(campaign, repository, onDismiss = { selectedTaskCampaign = null }, onMessage = { message = it })
    }

    if (showCreate) {
        CreateCampaignDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, url, description, category, target, minAndroid ->
                withContext(Dispatchers.IO) {
                    repository.createCampaign(name, url, description, category, target, minAndroid)
                }
            },
            onCreated = {
                message = "Kampanya oluşturuldu ve yayına alındı."
                refresh++
            }
        )
    }
}

@Composable
private fun CampaignCard(campaign: Campaign, repository: CampaignRepository, myTests: Boolean, refreshKey: Int, onApply: () -> Unit, onConfirmOptIn: () -> Unit, onCheckIn: () -> Unit, onOpenTasks: () -> Unit) {
    var tracking by remember(campaign.id) { mutableStateOf<CampaignTracking?>(null) }
    var checkinDates by remember(campaign.id) { mutableStateOf<Set<String>>(emptySet()) }
    var shareAccountEmail by remember(campaign.id) { mutableStateOf(false) }
    LaunchedEffect(campaign.id, refreshKey, myTests) {
        if (myTests && campaign.membershipStatus != null) {
            shareAccountEmail = withContext(Dispatchers.IO) { repository.campaignAccountEmailConsent(campaign.id) }.first
        }
        if (myTests && campaign.membershipStatus in listOf("opted_in", "active", "completed")) {
            tracking = withContext(Dispatchers.IO) { repository.trackingSnapshot(campaign.id) }.first
            checkinDates = withContext(Dispatchers.IO) { repository.campaignCheckinDates(campaign.id) }.first
        }
    }
    val todayUtc = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
    val checkedToday = todayUtc in checkinDates
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
                if (campaign.membershipStatus != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = shareAccountEmail, onCheckedChange = { shareAccountEmail = it })
                        Text("Hesap Gmail adresimi kampanya geliştiricisiyle paylaş", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        val selected = shareAccountEmail
                        kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch {
                            val error = withContext(Dispatchers.IO) { repository.setCampaignAccountEmailConsent(campaign.id, selected) }
                            if (error != null) shareAccountEmail = !selected
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Gmail paylaşım iznini kaydet") }
                }
                if (campaign.membershipStatus == "accepted") {
                    Button(onClick = onConfirmOptIn, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Play testine katıldım")
                    }
                }
                if (campaign.membershipStatus in listOf("opted_in", "active", "completed")) {
                    tracking?.let { progress ->
                        val startDate = progress.startedAt?.substringBefore("T")
                        val endDate = startDate?.let { plusDaysForDisplay(it, progress.requiredDays) }
                        Text("Test durumu: ${progress.status}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Text("İlerleme: ${progress.elapsedDays.coerceAtMost(progress.requiredDays)}/${progress.requiredDays} gün · Tester: ${progress.activeTesters}/${progress.requiredTesters}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Teste başlangıç: ${startDate ?: "Henüz başlamadı"} · Bitiş: ${endDate ?: "Bekleniyor"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        campaign.joinedAt?.substringBefore("T")?.let { Text("Katılım tarihi: $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            when (progress.status) {
                                "completed" -> "25 BP ödülü tamamlandı"
                                "running", "paused" -> "Bekleyen ödül: 25 BP"
                                else -> "Ödül, test koşulları tamamlandığında verilir"
                            },
                            color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp
                        )
                    }
                    Text("Günlük bildirim: ${checkinDates.size} gün", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenTasks, modifier = Modifier.fillMaxWidth()) { Text("Görevler ve geri bildirim") }
                    Button(onClick = onCheckIn, enabled = !checkedToday, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(if (checkedToday) "Bugünkü test bildirimi gönderildi" else "Bugün test yaptığımı bildir")
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignTaskDialog(
    campaign: Campaign,
    repository: CampaignRepository,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<CampaignTask>>(emptyList()) }
    var tracking by remember { mutableStateOf<CampaignTracking?>(null) }
    var loading by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }
    LaunchedEffect(campaign.id) {
        val result = withContext(Dispatchers.IO) { repository.tasksForCampaign(campaign.id) }
        tasks = result.first
        result.second?.let(onMessage)
        val clock = withContext(Dispatchers.IO) { repository.trackingSnapshot(campaign.id) }
        tracking = clock.first
        clock.second?.let(onMessage)
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${campaign.appName} · Test görevleri") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tracking?.let { snapshot ->
                    Text("14 günlük takip: ${snapshot.elapsedDays}/${snapshot.requiredDays} gün · Aktif tester: ${snapshot.activeTesters}/${snapshot.requiredTesters}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    Text("Durum: ${snapshot.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (loading) item { CircularProgressIndicator() }
                    items(tasks, key = { it.id }) { task ->
                        Column {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Text(task.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            if (task.completed) Text("Görev gönderildi / onaylandı", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                            else TextButton(onClick = {
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { repository.submitTask(task.id, "Görev tamamlandı.") }
                                    onMessage(error ?: "Görev tamamlanması geliştirici onayına gönderildi.")
                                    if (error == null) {
                                        val updated = withContext(Dispatchers.IO) { repository.tasksForCampaign(campaign.id) }
                                        tasks = updated.first
                                    }
                                }
                            }) { Text("Tamamlandı olarak gönder") }
                        }
                    }
                    item {
                        Text("Genel geri bildirimin", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(feedback, { feedback = it }, label = { Text("Deneyimini ve sorunları yaz") }, minLines = 3)
                        Text("Puan: $rating / 5")
                        Row {
                            (1..5).forEach { score ->
                                TextButton(onClick = { rating = score }) { Text(if (score == rating) "[$score]" else "$score") }
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { repository.sendFeedback(campaign.id, feedback, rating) }
                                    onMessage(error ?: "Geri bildirimin geliştiriciye iletildi.")
                                    if (error == null) feedback = ""
                                }
                            },
                            enabled = feedback.trim().length >= 5,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Geri bildirimi gönder") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun CreateCampaignDialog(
    onDismiss: () -> Unit,
    onCreate: suspend (String, String, String, String, Int, Int?) -> String?,
    onCreated: () -> Unit
) {
    val submitScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var target by remember { mutableStateOf("12") }
    var android by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "publish-button-scale"
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).imePadding(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Yeni test kampanyası", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            Button(
                enabled = !submitting,
                interactionSource = interactionSource,
                modifier = Modifier.graphicsLayer(scaleX = buttonScale, scaleY = buttonScale),
                onClick = {
                    val count = target.toIntOrNull()
                    val min = android.takeIf { it.isNotBlank() }?.toIntOrNull()
                    when {
                        name.trim().length < 2 -> error = "Uygulama adını gir."
                        !url.trim().startsWith("https://") -> error = "Google Play bağlantısı https:// ile başlamalı."
                        count == null || count !in 12..1000 -> error = "Kapalı test hedefi en az 12, en fazla 1000 tester olmalı."
                        android.isNotBlank() && (min == null || min < 1) -> error = "Android sürümünü sayı olarak gir."
                        else -> submitScope.launch {
                            submitting = true
                            error = runCatching {
                                onCreate(name.trim(), url.trim(), description.trim(), category.ifBlank { "other" }, count, min)
                            }.getOrElse { it.message ?: "Kampanya yayınlanırken beklenmeyen bir hata oluştu." }
                            if (error == null) {
                                onCreated()
                                onDismiss()
                            }
                            submitting = false
                        }
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text(" Yayınlanıyor…")
                } else Text("Yayınla")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}

@Composable
fun MyTestsHub() {
    // Başlık ve içerik aynı kaydırılabilir listede; üstte ikinci bir sabit başlık alanı yok.
    DeveloperCampaignsScreen()
}

private data class MyGroupProgress(
    val group: com.hzyazilim.betalink.core.data.TesterGroup,
    val snapshot: com.hzyazilim.betalink.core.data.GroupSnapshot,
    val checkinDates: Set<String>
)

@Composable
private fun MyGroupTestsScreen() {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<List<MyGroupProgress>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        val groupsResult = withContext(Dispatchers.IO) { repository.listGroups() }
        message = groupsResult.second
        val approved = groupsResult.first.filter { it.membershipStatus == "approved" && !it.isCreator }
        val rows = mutableListOf<MyGroupProgress>()
        for (group in approved) {
            val snapshotResult = withContext(Dispatchers.IO) { repository.groupSnapshot(group.id) }
            val snapshot = snapshotResult.first
            if (snapshot != null) {
                val checkins = withContext(Dispatchers.IO) { repository.groupCheckinDates(group.id) }
                rows += MyGroupProgress(group, snapshot, checkins.first)
            } else if (snapshotResult.second != null) {
                message = snapshotResult.second
            }
        }
        progress = rows
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("TESTÇİ OLARAK KATILDIĞIN GRUPLAR", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
        Text("Grup test takibin", style = MaterialTheme.typography.headlineSmall)
        WalletStatusCard()
        Text("Günlük bildirimin ve 14 günlük grup sürecin burada takip edilir. Günlük bildirim kendi beyanındır; otomatik görev teslimleri ayrıca kaydedilir.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (progress.isEmpty()) Text("Henüz onaylanmış bir tester grubuna katılmadın.", modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(progress, key = { it.group.id }) { item ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
                val checkedToday = today in item.checkinDates
                val start = item.snapshot.startedAt?.substringBefore("T")
                val end = start?.let { plusDaysForDisplay(it, item.snapshot.requiredDays - 1) }
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.group.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (item.snapshot.status) {
                                "running" -> "Test devam ediyor"
                                "completed" -> "14 günlük test tamamlandı"
                                "paused" -> "Test duraklatıldı"
                                else -> "15 tester bekleniyor"
                            },
                            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp
                        )
                        Text("İlerleme: ${item.snapshot.elapsedDays.coerceAtMost(item.snapshot.requiredDays)}/${item.snapshot.requiredDays} gün · ${item.snapshot.approvedMembers}/${item.snapshot.requiredMembers} tester", fontSize = 11.sp)
                        Text("Başlangıç: ${start ?: "Henüz başlamadı"} · Bitiş: ${end ?: "Bekleniyor"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Günlük bildirim: ${item.checkinDates.size} gün", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (item.snapshot.status) {
                                "completed" -> "25 BP grup ödülü tamamlandı"
                                "running", "paused" -> "Bekleyen ödül: 25 BP"
                                else -> "Ödül, 15 onaylı tester ve 14 günlük süreç tamamlandığında verilir"
                            },
                            color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { repository.checkInGroupToday(item.group.id) }
                                    message = result.second ?: if (result.first == true) "Bugünkü grup test bildirimin kaydedildi." else "Bugünkü bildirimin zaten kayıtlı."
                                    if (result.second == null) refresh++
                                }
                            },
                            enabled = !checkedToday && item.snapshot.status in listOf("running", "paused"),
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                        ) { Text(if (checkedToday) "Bugünkü bildirim gönderildi" else "Bugün test yaptığımı bildir") }
                    }
                }
            }
        }
    }
}

private fun plusDaysForDisplay(isoDate: String, days: Int): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(isoDate) ?: return isoDate
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.time = date
        calendar.add(java.util.Calendar.DAY_OF_YEAR, days.coerceAtLeast(0))
        parser.format(calendar.time)
    } catch (_: Exception) { isoDate }
}

@Composable
private fun DeveloperReviewDialog(
    campaign: Campaign,
    repository: CampaignRepository,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var submissions by remember { mutableStateOf<List<TaskSubmission>>(emptyList()) }
    var feedbackItems by remember { mutableStateOf<List<FeedbackItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(campaign.id) {
        val tasks = withContext(Dispatchers.IO) { repository.taskSubmissions(campaign.id) }
        submissions = tasks.first
        tasks.second?.let(onMessage)
        val feedback = withContext(Dispatchers.IO) { repository.feedbackForCampaign(campaign.id) }
        feedbackItems = feedback.first
        feedback.second?.let(onMessage)
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${campaign.appName} · İnceleme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) CircularProgressIndicator()
                Text("Görev teslimleri", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (submissions.isEmpty()) item { Text("Henüz görev teslimi yok.") }
                    items(submissions, key = { it.id }) { submission ->
                        Column {
                            Text(submission.taskTitle, style = MaterialTheme.typography.bodyMedium)
                            Text("Tester ${submission.testerId.take(8)}… · ${submission.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            if (submission.status == "submitted") {
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewTaskSubmission(submission.id, true) }
                                            onMessage(error ?: "Görev onaylandı.")
                                            if (error == null) submissions = withContext(Dispatchers.IO) { repository.taskSubmissions(campaign.id) }.first
                                        }
                                    }) { Text("Onayla") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewTaskSubmission(submission.id, false) }
                                            onMessage(error ?: "Düzeltme istendi.")
                                            if (error == null) submissions = withContext(Dispatchers.IO) { repository.taskSubmissions(campaign.id) }.first
                                        }
                                    }) { Text("Düzeltme iste") }
                                }
                            }
                        }
                    }
                    item { Text("Tester geri bildirimleri", style = MaterialTheme.typography.titleSmall) }
                    if (feedbackItems.isEmpty()) item { Text("Henüz geri bildirim yok.") }
                    items(feedbackItems, key = { it.id }) { item ->
                        var reply by remember(item.id) { mutableStateOf(item.reply.orEmpty()) }
                        Column {
                            Text("${item.rating ?: "—"}/5 · ${item.status}", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                            Text(item.body, fontSize = 13.sp)
                            Row {
                                TextButton(onClick = {
                                    scope.launch {
                                        val error = withContext(Dispatchers.IO) {
                                            repository.reportUser(item.testerId, campaign.id, "suspicious_feedback", "Geri bildirim geliştirici tarafından incelenmek üzere bildirildi.")
                                        }
                                        onMessage(error ?: "Geri bildirim yönetici incelemesine gönderildi.")
                                    }
                                }) { Text("Şikâyet et") }
                                TextButton(onClick = {
                                    scope.launch {
                                        val error = withContext(Dispatchers.IO) { repository.blockUser(item.testerId) }
                                        onMessage(error ?: "Tester engellendi.")
                                    }
                                }) { Text("Engelle") }
                            }
                            OutlinedTextField(reply, { reply = it }, label = { Text("Geliştirici yanıtı") }, minLines = 2)
                            Button(
                                onClick = {
                                    scope.launch {
                                        val error = withContext(Dispatchers.IO) { repository.replyToFeedback(item.id, reply, true) }
                                        onMessage(error ?: "Yanıt kaydedildi ve geri bildirim çözüldü.")
                                        if (error == null) feedbackItems = withContext(Dispatchers.IO) { repository.feedbackForCampaign(campaign.id) }.first
                                    }
                                },
                                enabled = reply.trim().isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Yanıtla ve çözüldü işaretle") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun DeveloperTesterReliabilityDialog(campaignId: String, testerId: String, repository: CampaignRepository, onDismiss: () -> Unit) {
    var data by remember { mutableStateOf<com.hzyazilim.betalink.core.data.TesterReliability?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(campaignId,testerId) {
        val result = withContext(Dispatchers.IO) { repository.testerReliabilityForCampaign(campaignId,testerId) }
        data = result.first
        error = result.second
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tester güvenilirlik profili") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                data?.let {
                    Text("Katıldığı test: ${it.testsJoined}")
                    Text("Tamamladığı test: ${it.testsCompleted}")
                    Text("Devamsızlık nedeniyle ayrıldığı: ${it.testsDropped}")
                    Text("Günlük bildirim: ${it.dailyCheckins} · Kaçırılan gün: ${it.missedDays}")
                    Text("Katılım güvenilirliği: ${it.attendancePercent}%")
                    Text("İtibar puanı: ${it.reputationPoints}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun DeveloperCampaignReportDialog(campaign: Campaign, repository: CampaignRepository, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    var report by remember { mutableStateOf<com.hzyazilim.betalink.core.data.CampaignReport?>(null) }
    var feedback by remember { mutableStateOf<com.hzyazilim.betalink.core.data.FeedbackSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(campaign.id) {
        val r = withContext(Dispatchers.IO) { repository.campaignReport(campaign.id) }
        report = r.first
        r.second?.let(onMessage)
        val f = withContext(Dispatchers.IO) { repository.campaignFeedbackSummary(campaign.id) }
        feedback = f.first
        f.second?.let(onMessage)
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${campaign.appName} · Test raporu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (loading) CircularProgressIndicator()
                report?.let {
                    Text("Katılımcı özeti", style = MaterialTheme.typography.titleSmall)
                    Text("Aktif tester: ${it.activeTesters} / ${campaign.targetTesters}")
                    Text("Yedek tester: ${it.backupTesters} · Ayrılan: ${it.droppedTesters}")
                    Text("Günlük test bildirimleri: ${it.totalCheckins}")
                    Text("Geri bildirim: ${it.feedbackCount} · Ortalama puan: ${it.averageRating?.toString() ?: "Henüz yok"}")
                    Text("Hata: ${it.bugReports} · Öneri: ${it.suggestions}")
                }
                feedback?.let {
                    Text("Geri bildirim özeti", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 5.dp))
                    Text("Deneyim: ${it.experiences} · Çözülen: ${it.resolved}")
                    Text("Hata bildirimi: ${it.bugs} · Öneri: ${it.suggestions}")
                    Text("Ortalama değerlendirme: ${it.averageRating?.toString() ?: "Henüz yok"}")
                }
                if (!loading && report == null) Text("Rapor verisi henüz oluşmadı.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun DeveloperCampaignsScreen() {
    val context = LocalContext.current
    val repository = remember { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf<List<Campaign>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var selectedCampaign by remember { mutableStateOf<Campaign?>(null) }
    var selectedReviewCampaign by remember { mutableStateOf<Campaign?>(null) }
    var selectedReportCampaign by remember { mutableStateOf<Campaign?>(null) }
    var reliabilityTesterId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var applicants by remember { mutableStateOf<List<com.hzyazilim.betalink.core.data.CampaignMember>>(emptyList()) }

    LaunchedEffect(refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) { repository.myDeveloperCampaigns() }
        campaigns = result.first
        message = result.second
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("GELİŞTİRİCİ PANELİ", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
            Text("Kampanyaların", style = MaterialTheme.typography.headlineSmall)
            WalletStatusCard()
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("＋ Kampanya oluştur · 50 BP")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (loading) {
                CircularProgressIndicator(Modifier.padding(20.dp))
            } else if (campaigns.isEmpty()) {
                Text(
                    "Henüz kampanya oluşturmadın.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(campaigns, key = { it.id }) { campaign ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text(campaign.appName, style = MaterialTheme.typography.titleMedium)
                    Text("Durum: ${campaign.status} · Hedef: ${campaign.targetTesters} tester", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Button(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { repository.applicants(campaign.id) }
                            applicants = result.first
                            message = result.second
                            selectedCampaign = campaign
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Başvuruları yönet") }
                    TextButton(onClick = { selectedReviewCampaign = campaign }, modifier = Modifier.fillMaxWidth()) { Text("Görev ve geri bildirimleri yönet") }
                    TextButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { repository.processCampaignAttendance(campaign.id) }
                            message = result.second ?: "Devamsızlık kontrolü tamamlandı. Katılımcı durumları güncellendi."
                            selectedReportCampaign = campaign
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Devamsızlık kontrolü ve test raporu") }
                }
            }
        }
    }

    if (showCreate) {
        CreateCampaignDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, url, description, category, target, minAndroid ->
                withContext(Dispatchers.IO) {
                    repository.createCampaign(name, url, description, category, target, minAndroid)
                }
            },
            onCreated = {
                message = "Kampanya oluşturuldu."
                refresh++
            }
        )
    }

    selectedReviewCampaign?.let { campaign ->
        DeveloperReviewDialog(campaign, repository, onDismiss = { selectedReviewCampaign = null }, onMessage = { message = it })
    }
    selectedReportCampaign?.let { campaign ->
        DeveloperCampaignReportDialog(campaign, repository, onDismiss = { selectedReportCampaign = null }, onMessage = { message = it })
    }
    reliabilityTesterId?.let { testerId ->
        val campaignId = selectedCampaign?.id
        if (campaignId != null) DeveloperTesterReliabilityDialog(campaignId,testerId,repository,onDismiss = { reliabilityTesterId = null })
    }

    selectedCampaign?.let { campaign ->
        AlertDialog(
            onDismissRequest = { selectedCampaign = null },
            title = { Text("${campaign.appName} · Başvurular") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (applicants.isEmpty()) item { Text("Henüz başvuru yok.") }
                    items(applicants, key = { it.id }) { applicant ->
                        Column {
                            Text(applicant.displayName.ifBlank { "Betalink kullanıcısı" }, style = MaterialTheme.typography.titleSmall)
                            Text("Hesap Gmail\u0027i: " + applicant.accountEmail.ifBlank { "Kullanıcı paylaşmamış" }, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                            Text("İletişim e-postası: " + applicant.email.ifBlank { "Paylaşılmamış" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            Text("Durum: " + when(applicant.status) { "applied" -> "Başvurdu"; "accepted" -> "Kabul edildi"; "waitlisted" -> "Yedek listede"; "rejected" -> "Reddedildi"; else -> applicant.status }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            TextButton(onClick = { reliabilityTesterId = applicant.testerId }) { Text("Güvenilirlik profilini görüntüle") }
                            if (applicant.status == "applied" || applicant.status == "waitlisted") {
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewApplicant(applicant.id, "accepted") }
                                            message = error ?: "Başvuru kabul edildi."
                                            if (error == null) {
                                                applicants = withContext(Dispatchers.IO) { repository.applicants(campaign.id) }.first
                                                refresh++
                                            }
                                        }
                                    }) { Text("Kabul et") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewApplicant(applicant.id, "waitlisted") }
                                            message = error ?: "Yedek listeye alındı."
                                            if (error == null) applicants = withContext(Dispatchers.IO) { repository.applicants(campaign.id) }.first
                                        }
                                    }) { Text("Yedek") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewApplicant(applicant.id, "rejected") }
                                            message = error ?: "Başvuru reddedildi."
                                            if (error == null) applicants = withContext(Dispatchers.IO) { repository.applicants(campaign.id) }.first
                                        }
                                    }) { Text("Reddet") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedCampaign = null }) { Text("Kapat") } }
        )
    }
}
