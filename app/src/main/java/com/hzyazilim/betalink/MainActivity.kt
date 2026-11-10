package com.hzyazilim.betalink

import android.os.Bundle
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hzyazilim.betalink.ui.auth.AuthGate
import com.hzyazilim.betalink.core.auth.BetalinkUser
import com.hzyazilim.betalink.core.data.CampaignRepository
import com.hzyazilim.betalink.core.data.BetaWallet
import com.hzyazilim.betalink.core.data.TesterReliability
import com.hzyazilim.betalink.ui.campaigns.MyTestsHub
import com.hzyazilim.betalink.ui.campaigns.CampaignBrowserScreen
import com.hzyazilim.betalink.ui.groups.GroupsScreen
import com.hzyazilim.betalink.ui.people.PeopleScreen
import com.hzyazilim.betalink.ui.notifications.NotificationsDialog
import com.hzyazilim.betalink.ui.admin.AdminScreen
import com.hzyazilim.betalink.ui.support.SupportDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BetalinkColors = darkColorScheme(
    primary = Color(0xFF91A2FF),
    secondary = Color(0xFF67D9C5),
    background = Color(0xFF0A1020),
    surface = Color(0xFF151E33),
    surfaceVariant = Color(0xFF202B45),
    onPrimary = Color(0xFF10152B),
    onBackground = Color(0xFFF4F6FF),
    onSurface = Color(0xFFF4F6FF)
)

private data class AppTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    AppTab("Ana sayfa", Icons.Filled.Home),
    AppTab("Test bul", Icons.Filled.Search),
    AppTab("Gruplar", Icons.Filled.Groups),
    AppTab("Kişiler", Icons.Filled.PersonAdd),
    AppTab("Testlerim", Icons.Filled.Checklist),
    AppTab("Profil", Icons.Filled.Person)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("betalink_updates", "Betalink bildirimleri", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Test davetleri, grup durumu ve görev bildirimleri"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        setContent {
            MaterialTheme(colorScheme = BetalinkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthGate(initialInviteToken = intent?.data?.takeIf { it.scheme == "betalink" && it.host == "group-invite" }?.lastPathSegment)
                }
            }
        }
    }
}

@Composable
fun BetalinkApp(currentUser: BetalinkUser, isAdmin: Boolean, openPeopleInitially: Boolean = false, initialInviteToken: String? = null, onSignOut: suspend () -> Unit, onDeleteAccount: suspend () -> String?) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(if (openPeopleInitially) 3 else 0) }
    var deleteMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var showNotifications by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showAdminPanel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSupport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var termsAccepted by remember(currentUser.id) { mutableStateOf<Boolean?>(null) }
    var termsError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val termsRepository = remember(context) { CampaignRepository(context) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(currentUser.id) {
        termsAccepted = null
        val result = withContext(Dispatchers.IO) { termsRepository.currentTermsAccepted() }
        termsAccepted = result.first ?: false
        termsError = result.second
    }
    val activity = context as? ComponentActivity
    val notificationPrefs = remember(context) { context.getSharedPreferences("betalink_preferences", Context.MODE_PRIVATE) }
    var showNotificationPermissionInfo by rememberSaveable { mutableStateOf(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !notificationPrefs.getBoolean("notification_prompted", false)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationPrefs.edit().putBoolean("notification_prompted", true).apply() }

    BackHandler(enabled = true) {
        when {
            showNotifications -> showNotifications = false
            showSupport -> showSupport = false
            showAdminPanel -> showAdminPanel = false
            selectedTab != 0 -> selectedTab = 0
            else -> showExitDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF11192B)) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index && !showAdminPanel,
                        onClick = {
                            selectedTab = index
                            showAdminPanel = false
                        },
                        icon = { androidx.compose.material3.Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            when (termsAccepted) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                false -> Unit
                true -> {
                    if (showAdminPanel) {
                        AdminScreen(onClose = { showAdminPanel = false })
                    } else when (selectedTab) {
                        0 -> HomeScreen(onExplore = { selectedTab = 1 }, onMyTests = { selectedTab = 4 }, onNotifications = { showNotifications = true })
                        1 -> CampaignBrowserScreen(myTests = false)
                        2 -> GroupsScreen(onOpenContacts = { selectedTab = 3 })
                        3 -> PeopleScreen(inviteToken = initialInviteToken)
                        4 -> MyTestsHub()
                        else -> ProfileScreen(currentUser, isAdmin = isAdmin, deleteMessage = deleteMessage, onAdminPanel = { showAdminPanel = true }, onSupport = { showSupport = true }, onSignOut = { scope.launch { onSignOut() } }, onDeleteAccount = { scope.launch { deleteMessage = onDeleteAccount() ?: "Hesap silme işlemi tamamlandı." } })
                    }
                }
            }
        }
    }
    if (termsAccepted == false) {
        TermsAcceptanceDialog(
            error = termsError,
            onOpenTerms = { uriHandler.openUri(CampaignRepository.TERMS_URL) },
            onAccept = {
                scope.launch {
                    val error = withContext(Dispatchers.IO) { termsRepository.acceptCurrentTerms() }
                    termsError = error
                    if (error == null) termsAccepted = true
                }
            },
            onSignOut = { scope.launch { onSignOut() } }
        )
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Betalink'ten çıkılsın mı?") },
            text = { Text("Uygulamadan çıkmak istediğine emin misin?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; activity?.finish() }) { Text("Çıkış yap") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Uygulamaya dön") }
            }
        )
    }
    if (showNotifications) NotificationsDialog(onDismiss = { showNotifications = false })
    if (showNotificationPermissionInfo) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionInfo = false; notificationPrefs.edit().putBoolean("notification_prompted", true).apply() },
            title = { Text("Betalink bildirimleri") },
            text = { Text("Test davetlerini, grup başlangıç ve tamamlanma durumlarını, görevleri ve önemli hesap uyarılarını zamanında görebilmek için bildirim izni verebilirsin.") },
            confirmButton = { TextButton(onClick = { showNotificationPermissionInfo = false; notificationPrefs.edit().putBoolean("notification_prompted", true).apply(); if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("İzin ver") } },
            dismissButton = { TextButton(onClick = { showNotificationPermissionInfo = false; notificationPrefs.edit().putBoolean("notification_prompted", true).apply() }) { Text("Şimdi değil") } }
        )
    }
    if (showSupport) SupportDialog(onDismiss = { showSupport = false })
}

@Composable
private fun TermsAcceptanceDialog(error: String?, onOpenTerms: () -> Unit, onAccept: () -> Unit, onSignOut: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Betalink kullanım koşulları") },
        text = {
            Column {
                Text("Betalink'i kullanmadan önce Kullanım Koşulları ve Topluluk Kuralları'nı okuyup kabul etmelisin.")
                Text("Kurallar; dürüst test katılımı, sahte check-in ve spam yasağı, kişisel verilerin korunması, uygunsuz içeriklerin bildirilmesi ve topluluk moderasyonunu kapsar.", modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) { Text("Koşulların tamamını oku") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text("Okudum ve kabul ediyorum.", fontSize = 13.sp)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = checked) { Text("Kabul et ve devam et") }
        },
        dismissButton = { TextButton(onClick = onSignOut) { Text("Çıkış yap") } }
    )
}

@Composable
private fun HomeScreen(onExplore: () -> Unit, onMyTests: () -> Unit, onNotifications: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    var wallet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<BetaWallet?>(null) }
    var walletMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { repository.walletSnapshot() }
        wallet = result.first
        walletMessage = result.second
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).background(Color(0xFF26345B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 23.sp)
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("BETALINK", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                    Text("Test et. Geri bildirim ver. Geliştir.", color = Color(0xFF9BA8C2), fontSize = 11.sp)
                }
                IconButton(onClick = onNotifications) {
                    androidx.compose.material3.Icon(Icons.Filled.Notifications, contentDescription = "Bildirimler", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Uygulamalar\nbirlikte gelişir.", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Text(
                "Gerçek tester toplulukları ve uygulama geliştiricileri tek platformda buluşur.",
                color = Color(0xFFB5BED4), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18263D))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("BETA PUAN", color = Color(0xFF9BA8C2), fontSize = 10.sp, letterSpacing = 1.5.sp)
                            Text((wallet?.balance ?: 0).toString() + " BP", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((wallet?.completedSupportTests ?: 0).toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Tamamlanan destek testi", fontSize = 10.sp, color = Color(0xFF9BA8C2))
                        }
                    }
                    Text("Kampanya oluşturma 50 BP, tester grubu oluşturma 30 BP, açık gruba katılım 5 BP. Ek test şartı yok; yalnızca yeterli BP bakiyesi aranır. Her tamamlanan test 25 BP kazandırır.", color = Color(0xFFB9C4DA), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    walletMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2845))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Bir sonraki test fırsatını keşfet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Cihazına uygun kapalı test kampanyalarına göz at. Katıldığın her kampanyayı ayrı ayrı takip et.",
                        color = Color(0xFFB9C4DA), fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp)
                    )
                    Button(onClick = onExplore, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Text("Test kampanyalarını keşfet")
                    }
                }
            }
        }
        item {
            Text("Betalink ile", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        item { FeatureCard("Bağımsız 14 günlük takip", "Her kampanyanın süresi ve katılımcı durumu ayrı izlenir.") }
        item { FeatureCard("Görev ve geri bildirim", "Kurulum, kullanım adımları ve dürüst geri bildirim tek yerde.") }
        item { FeatureCard("Topluluk ve yedek liste", "Gruplar, davetler ve ayrılan testerlar için yedek katılımcılar.") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Geliştirici misin?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Kampanya oluşturma ve tester yönetimi de aynı hesapta yer alacak.", color = Color(0xFFB5BED4), fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    Button(onClick = onMyTests, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("Paneli görüntüle")
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun FeatureCard(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF151E33), RoundedCornerShape(16.dp)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(Color(0xFF26345B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Color(0xFFADB8CE), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun EmptyListScreen(eyebrow: String, title: String, message: String, action: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(eyebrow, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
        Text(title, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(message, color = Color(0xFFB5BED4), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text(action, color = Color(0xFF8492AE), fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun ProfileScreen(currentUser: BetalinkUser, isAdmin: Boolean, deleteMessage: String?, onAdminPanel: () -> Unit, onSupport: () -> Unit, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val repository = remember(context) { CampaignRepository(context) }
    var reputation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var reliability by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<TesterReliability?>(null) }
    var allowGroupInvites by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var invitePreferenceMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    LaunchedEffect(currentUser.id) {
        reputation = withContext(Dispatchers.IO) { repository.myReputationScore().first }
        reliability = withContext(Dispatchers.IO) { repository.testerReliability().first }
        val preference = withContext(Dispatchers.IO) { repository.groupInvitePreference() }
        allowGroupInvites = preference.first
        invitePreferenceMessage = preference.second
    }
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("HESAP", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
        Text(currentUser.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(currentUser.email, color = Color(0xFFB5BED4), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text("İtibar puanın: $reputation", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("TESTER GÜVENİLİRLİK PROFİLİ", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Katıldığın test: ${reliability?.testsJoined ?: 0} · Tamamlanan: ${reliability?.testsCompleted ?: 0}", fontSize = 13.sp)
                Text("Devamsızlık nedeniyle ayrıldığın: ${reliability?.testsDropped ?: 0}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Günlük test bildirimleri: ${reliability?.dailyCheckins ?: 0} · Kaçırılan gün: ${reliability?.missedDays ?: 0}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Katılım güvenilirliği: ${reliability?.attendancePercent ?: 100}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Grup davetleri", fontWeight = FontWeight.SemiBold)
                    Text(if (allowGroupInvites) "Diğer kullanıcılar seni gruplarına davet edebilir." else "Grup davetleri kapalı. Kişiler listesinde davet alamazsın.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                androidx.compose.material3.Switch(checked = allowGroupInvites, onCheckedChange = { enabled ->
                    scope.launch {
                        val error = withContext(Dispatchers.IO) { repository.setGroupInvitePreference(enabled) }
                        if (error == null) allowGroupInvites = enabled
                        invitePreferenceMessage = error ?: "Davet tercihin kaydedildi."
                    }
                })
            }
            invitePreferenceMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) }
        }
        Text(
            "Tek hesapla tester ve geliştirici olarak yer alabilirsin. Hesabını silersen profilin ve ilişkili kişisel kayıtların veritabanından kaldırılır.",
            color = Color(0xFFB5BED4), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
        )
        deleteMessage?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text("Destek taleplerim") }
        TextButton(onClick = { uriHandler.openUri(CampaignRepository.TERMS_URL) }, modifier = Modifier.fillMaxWidth()) { Text("Kullanım koşulları ve topluluk kuralları") }
        TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/privacy") }, modifier = Modifier.fillMaxWidth()) { Text("Gizlilik politikası") }
        TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/account-deletion") }, modifier = Modifier.fillMaxWidth()) { Text("Hesap ve veri silme sayfası") }
        if (isAdmin) Button(onClick = onAdminPanel, modifier = Modifier.fillMaxWidth()) { Text("Yönetici paneli") }
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Güvenli çıkış yap") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Hesabımı ve verilerimi sil", color = Color(0xFFFF8A8A))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hesap kalıcı olarak silinsin mi?") },
            text = { Text("Profilin, kampanya kayıtların ve hesabına bağlı kişisel verilerin silinir. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteAccount() }) {
                    Text("Kalıcı olarak sil", color = Color(0xFFFF8A8A))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            }
        )
    }
}
