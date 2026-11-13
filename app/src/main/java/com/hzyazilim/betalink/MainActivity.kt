package com.hzyazilim.betalink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hzyazilim.betalink.core.auth.BetalinkUser
import com.hzyazilim.betalink.core.data.BetaWallet
import com.hzyazilim.betalink.core.data.CampaignRepository
import com.hzyazilim.betalink.core.data.TesterReliability
import com.hzyazilim.betalink.ui.admin.AdminScreen
import com.hzyazilim.betalink.ui.auth.AuthGate
import com.hzyazilim.betalink.ui.campaigns.CampaignBrowserScreen
import com.hzyazilim.betalink.ui.campaigns.MyTestsHub
import com.hzyazilim.betalink.ui.groups.GroupsScreen
import com.hzyazilim.betalink.ui.notifications.NotificationsDialog
import com.hzyazilim.betalink.ui.people.PeopleScreen
import com.hzyazilim.betalink.ui.support.SupportDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BetalinkColors = darkColorScheme(
    primary = Color(0xFF8B8CFF),
    onPrimary = Color(0xFF070B18),
    primaryContainer = Color(0xFF141B33),
    onPrimaryContainer = Color(0xFFE3E8FF),
    secondary = Color(0xFF2EE6A8),
    onSecondary = Color(0xFF070B18),
    secondaryContainer = Color(0xFF111830),
    onSecondaryContainer = Color(0xFF2EE6A8),
    tertiary = Color(0xFFFF7A6B),
    onTertiary = Color(0xFF070B18),
    background = Color(0xFF070B18),
    onBackground = Color(0xFFF4F6FF),
    surface = Color(0xFF141B33),
    onSurface = Color(0xFFF4F6FF),
    surfaceVariant = Color(0xFF10182E),
    onSurfaceVariant = Color(0xFF9AA3B8),
    outline = Color(0xFF35425F),
    outlineVariant = Color(0xFF202B45),
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF070B18)
)

private data class AppTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    AppTab("Ana sayfa", Icons.Filled.Home),
    AppTab("Test bul", Icons.Filled.Search),
    AppTab("Gruplar", Icons.Filled.Groups),
    AppTab("Toplum", Icons.Outlined.Groups),
    AppTab("Testlerim", Icons.Filled.Checklist),
    AppTab("Profil", Icons.Filled.Person)
)

class MainActivity : ComponentActivity() {
    private var authCallbackUri by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val data = intent.data
        if (data?.scheme == "betalink" && data.host == "auth-callback") {
            authCallbackUri = data.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.takeIf { it.scheme == "betalink" && it.host == "auth-callback" }?.let { authCallbackUri = it.toString() }
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
                    AuthGate(
                        initialInviteToken = intent?.data?.takeIf { it.scheme == "betalink" && it.host == "group-invite" }?.lastPathSegment,
                        authCallbackUri = authCallbackUri
                    )
                }
            }
        }
    }
}

@Composable
fun BetalinkApp(
    currentUser: BetalinkUser,
    isAdmin: Boolean,
    openPeopleInitially: Boolean = false,
    initialInviteToken: String? = null,
    onSignOut: suspend () -> Unit,
    onDeleteAccount: suspend () -> String?
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(if (openPeopleInitially) 3 else 0) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    var showNotifications by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }
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
    var showNotificationPermissionInfo by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !notificationPrefs.getBoolean("notification_prompted", false)
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationPrefs.edit().putBoolean("notification_prompted", true).apply()
    }

    BackHandler(enabled = true) {
        when {
            showNotifications -> showNotifications = false
            showSupport -> showSupport = false
            showAdminPanel -> showAdminPanel = false
            showHowItWorks -> showHowItWorks = false
            selectedTab != 0 -> selectedTab = 0
            else -> showExitDialog = true
        }
    }

    Scaffold(
        containerColor = Color(0xFF070B18),
        bottomBar = {
            if (!showAdminPanel) {
                BetalinkBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (termsAccepted) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2EE6A8)) }
                false -> Unit
                true -> {
                    AnimatedContent(
                        targetState = selectedTab to showAdminPanel,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) +
                                slideInHorizontally(animationSpec = tween(220)) { it / 14 }) togetherWith
                                (fadeOut(animationSpec = tween(160)) +
                                    slideOutHorizontally(animationSpec = tween(160)) { -it / 14 })
                        },
                        label = "betalink-screen-transition"
                    ) { (tab, adminVisible) ->
                        if (adminVisible) {
                            AdminScreen(onClose = { showAdminPanel = false })
                        } else when (tab) {
                            0 -> HomeScreen(
                                onExplore = { selectedTab = 1 },
                                onNotifications = { showNotifications = true },
                                onHowItWorks = { showHowItWorks = true }
                            )
                            1 -> BuildTestBulScreen()
                            2 -> BuildSimplePageScreen(
                                title = "Gruplar",
                                desc = "Tester gruplarını keşfet, katılım isteklerini yönet ve 14 günlük test sürecini takip et.",
                                content = { GroupsScreen(onOpenCommunity = { selectedTab = 3 }) }
                            )
                            3 -> BuildSimplePageScreen(
                                title = "Toplum",
                                desc = "Kayıtlı kullanıcıları keşfet, grup davetleri gönder ve gelen davetleri yönet.",
                                content = { PeopleScreen(inviteToken = initialInviteToken) }
                            )
                            4 -> BuildTestlerimScreen()
                            else -> BuildSimplePageScreen(
                                title = "Profil",
                                desc = "Profil bilgilerini, tester güvenilirliğini, gizlilik tercihlerini ve hesap ayarlarını yönet.",
                                content = {
                                    ProfileScreen(
                                        currentUser = currentUser,
                                        isAdmin = isAdmin,
                                        deleteMessage = deleteMessage,
                                        onAdminPanel = { showAdminPanel = true },
                                        onSupport = { showSupport = true },
                                        onSignOut = { scope.launch { onSignOut() } },
                                        onDeleteAccount = { scope.launch { deleteMessage = onDeleteAccount() ?: "Hesap silme işlemi tamamlandı." } }
                                    )
                                }
                            )
                        }
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

    if (showHowItWorks) {
        HowItWorksDialog(onDismiss = { showHowItWorks = false })
    }

    if (showNotifications) NotificationsDialog(onDismiss = { showNotifications = false })
    if (showNotificationPermissionInfo) {
        AlertDialog(
            onDismissRequest = {
                showNotificationPermissionInfo = false
                notificationPrefs.edit().putBoolean("notification_prompted", true).apply()
            },
            title = { Text("Betalink bildirimleri") },
            text = { Text("Test davetlerini, grup başlangıç ve tamamlanma durumlarını, görevleri ve önemli hesap uyarılarını zamanında görebilmek için bildirim izni verebilirsin.") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationPermissionInfo = false
                    notificationPrefs.edit().putBoolean("notification_prompted", true).apply()
                    if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("İzin ver") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotificationPermissionInfo = false
                    notificationPrefs.edit().putBoolean("notification_prompted", true).apply()
                }) { Text("Şimdi değil") }
            }
        )
    }
    if (showSupport) SupportDialog(onDismiss = { showSupport = false })
}

@Composable
private fun BetalinkBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF10182E),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { i, tab ->
                    val active = selectedTab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) Color(0xFF2EE6A8).copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onTabSelected(i) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (active) Color(0xFF2EE6A8) else Color(0xFF9AA3B8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (active) Color.White else Color(0xFF9AA3B8),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- ANA SAYFA ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    onExplore: () -> Unit,
    onNotifications: () -> Unit,
    onHowItWorks: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    var wallet by remember { mutableStateOf<BetaWallet?>(null) }
    var walletMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { repository.walletSnapshot() }
        wallet = result.first
        walletMessage = result.second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        TopBar(onNotifications = onNotifications)

        Spacer(Modifier.height(10.dp))

        // HERO Section
        Text(
            text = "Uygulamalar",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            lineHeight = 44.sp
        )
        Text(
            text = "birlikte gelişir.",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(Color(0xFF8B8CFF), Color(0xFF2EE6A8)))
            ),
            lineHeight = 44.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Gerçek tester toplulukları ve uygulama geliştiricileri tek platformda buluşur.",
            fontSize = 15.sp,
            color = Color(0xFF9AA3B8),
            lineHeight = 22.sp
        )

        // Chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipWidget("🔥  7 gün seri")
            ChipWidget("⭐  4.9 puan")
            ChipWidget("👥  12.4K tester")
        }

        Spacer(Modifier.height(6.dp))

        // Beta Puan Card
        BetaPuanCard(
            balance = wallet?.balance ?: 99970,
            completedTests = wallet?.completedSupportTests ?: 0,
            onEarnPoints = onExplore,
            onHowItWorks = onHowItWorks
        )

        // Keşfet Card
        KesfetCard(onExplore = onExplore)

        Spacer(Modifier.height(10.dp))

        // Bugün senin için Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bugün senin için",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            TextButton(onClick = onExplore) {
                Text("Tümü →", color = Color(0xFF8B8CFF), fontWeight = FontWeight.Bold)
            }
        }

        // Horizontal Cards List
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { TestCardWidget("Nova", "Sosyal • iOS", "+25 BP", "2 gün kaldı", Color(0xFF8B8CFF), "N", onJoin = onExplore) }
            item { TestCardWidget("FitTrack", "Sağlık • Android", "+25 BP", "5 gün kaldı", Color(0xFF2EE6A8), "F", onJoin = onExplore) }
            item { TestCardWidget("Pawly", "Evcil Hayvan", "+40 BP", "1 gün kaldı", Color(0xFFFF7A6B), "P", onJoin = onExplore) }
        }

        Spacer(Modifier.height(4.dp))

        // Canlı Topluluk Card
        LiveCommunityCard()
    }
}

@Composable
private fun TopBar(onNotifications: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gradient Circle Avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFF8B8CFF), Color(0xFF2EE6A8))),
                    shape = CircleShape
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A2340), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B",
                    color = Color(0xFF8B8CFF),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "BETALINK",
                fontWeight = FontWeight.Black,
                letterSpacing = 2.2.sp,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Test et. Geri bildirim ver. Geliştir.",
                color = Color(0xFF9AA3B8),
                fontSize = 12.sp
            )
        }

        // Level Badge
        Box(
            modifier = Modifier
                .background(Color(0xFF2EE6A8).copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Lv. 12 • Pro Tester",
                color = Color(0xFF2EE6A8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(10.dp))

        // Notification Icon with Badge
        Box {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF141B33), RoundedCornerShape(14.dp))
                    .clickable { onNotifications() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Bildirimler",
                    tint = Color(0xFF8B8CFF)
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = 30.dp, y = 8.dp)
                    .size(9.dp)
                    .background(Color(0xFFFF5252), CircleShape)
                    .border(1.5.dp, Color(0xFF070B18), CircleShape)
            )
        }
    }
}

@Composable
private fun ChipWidget(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BetaPuanCard(
    balance: Int,
    completedTests: Int,
    onEarnPoints: () -> Unit,
    onHowItWorks: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(Color(0xFF1A2340), Color(0xFF121A33))),
                shape = RoundedCornerShape(28.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BETA PUAN",
                        letterSpacing = 2.sp,
                        color = Color(0xFF9AA3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$balance BP",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF2EE6A8)
                    )
                }

                // Halka Progress
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 0.78f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF8B8CFF),
                        strokeWidth = 7.dp,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                    Text(
                        text = "%78",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tamamlanan destek testi",
                    color = Color(0xFF9AA3B8),
                    fontSize = 12.sp
                )
                Text(
                    text = completedTests.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Kampanya oluşturma 50 BP, tester grubu oluşturma 30 BP, açık gruba katılım 5 BP. Ek test şartı yok; yalnızca yeterli BP bakiyesi aranır. Her tamamlanan test 25 BP kazandırır.",
                lineHeight = 20.sp,
                color = Color(0xFFB9C0D1),
                fontSize = 13.5.sp
            )

            Spacer(Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CostChipWidget("Kampanya  50 BP", isGain = false)
                CostChipWidget("Grup  30 BP", isGain = false)
                CostChipWidget("Katılım  5 BP", isGain = false)
                CostChipWidget("Kazanç  +25 BP", isGain = true)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onEarnPoints,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2EE6A8),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Puan Kazan", fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = onHowItWorks,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Nasıl çalışır?")
                }
            }
        }
    }
}

@Composable
private fun CostChipWidget(text: String, isGain: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (isGain) Color(0xFF2EE6A8).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGain) Color(0xFF2EE6A8) else Color.White
        )
    }
}

@Composable
private fun KesfetCard(onExplore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFF6B6CFF), Color(0xFF9B5CFF), Color(0xFF2EE6A8))),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(22.dp)
    ) {
        Column {
            Text(
                text = "Bir sonraki test fırsatını keşfet",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 26.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Cihazına uygun kapalı test kampanyalarına göz at. Katıldığın her kampanyayı ayrı ayrı takip et.",
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 21.sp,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onExplore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(30.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "Test kampanyalarını keşfet  →",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun TestCardWidget(
    name: String,
    cat: String,
    bp: String,
    time: String,
    color: Color,
    letter: String,
    onJoin: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .background(Color(0xFF141B33), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(color.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        color = color,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .background(Color(0xFF2EE6A8).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = bp,
                        color = Color(0xFF2EE6A8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = name,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = cat,
                color = Color(0xFF9AA3B8),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = Color(0xFF9AA3B8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = time,
                    color = Color(0xFF9AA3B8),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B8CFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Katıl", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveCommunityCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111830), RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Overlapping Mini Avatars
            Box(modifier = Modifier.width(72.dp)) {
                MiniAvatar(0.dp, Color(0xFF8B8CFF), "A")
                MiniAvatar(18.dp, Color(0xFF2EE6A8), "M")
                MiniAvatar(36.dp, Color(0xFFFF7A6B), "+")
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Canlı Topluluk",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "2.340 kişi şu an test ediyor",
                    color = Color(0xFF9AA3B8),
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF2EE6A8).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF2EE6A8), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "CANLI",
                        color = Color(0xFF2EE6A8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniAvatar(offsetX: androidx.compose.ui.unit.Dp, bg: Color, text: String) {
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .size(32.dp)
            .background(bg, CircleShape)
            .border(1.5.dp, Color(0xFF111830), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

// ---------- DİĞER SEKMELER ----------
@Composable
private fun BuildTestBulScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Text(
            text = "Test bul",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Cihazına uygun kampanyaları filtrele.",
            color = Color(0xFF9AA3B8),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))

        // CampaignBrowserScreen owns its own vertical scrolling; keep it bounded.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CampaignBrowserScreen(myTests = false)
        }
    }
}

@Composable
private fun TestCardFullWidget(title: String, sub: String, letter: Char, avatarBg: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(Color(0xFF141B33), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(avatarBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = sub,
                    color = Color(0xFF9AA3B8),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B8CFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Katıl")
            }
        }
    }
}

@Composable
private fun BuildTestlerimScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Text(
            text = "Testlerim",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))

        // DeveloperCampaignsScreen owns a LazyColumn. Give it a bounded viewport;
        // wrapping it in this screen's verticalScroll caused Compose to measure it
        // with infinite height constraints and crash when opening Testlerim.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MyTestsHub()
        }
    }
}

@Composable
private fun ProgressRowWidget(title: String, progress: Float, status: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(Color(0xFF141B33), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = status,
                    color = Color(0xFF9AA3B8),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = Color(0xFF2EE6A8),
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
private fun BuildSimplePageScreen(
    title: String,
    desc: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = desc,
            color = Color(0xFF9AA3B8),
            lineHeight = 22.sp,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))

        // Child screens manage their own scrolling (PeopleScreen uses LazyColumn).
        // Keep them inside a finite-height viewport to avoid nested-scroll crashes.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun TermsAcceptanceDialog(
    error: String?,
    onOpenTerms: () -> Unit,
    onAccept: () -> Unit,
    onSignOut: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Betalink kullanım koşulları") },
        text = {
            Column {
                Text("Betalink'i kullanmadan önce Kullanım Koşulları ve Topluluk Kuralları'nı okuyup kabul etmelisin.")
                Text(
                    "Kurallar; dürüst test katılımı, sahte check-in ve spam yasağı, kişisel verilerin korunması, uygunsuz içeriklerin bildirilmesi ve topluluk moderasyonunu kapsar.",
                    modifier = Modifier.padding(top = 10.dp)
                )
                TextButton(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) { Text("Koşulların tamamını oku") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
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
private fun HowItWorksDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beta Puan Sistemi Nasıl Çalışır?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("• Kampanya oluşturma: 50 BP", fontSize = 14.sp)
                Text("• Tester grubu oluşturma: 30 BP", fontSize = 14.sp)
                Text("• Açık gruba katılım: 5 BP", fontSize = 14.sp)
                Text("• Kazanç: Tamamlanan her test 25 BP kazandırır.", fontSize = 14.sp, color = Color(0xFF2EE6A8), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ek test şartı yoktur; platformda kampanya veya grup açmak için yalnızca yeterli BP bakiyesi aranır.",
                    fontSize = 12.sp,
                    color = Color(0xFF9AA3B8)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Anladım", color = Color(0xFF2EE6A8)) }
        }
    )
}

@Composable
private fun ProfileScreen(
    currentUser: BetalinkUser,
    isAdmin: Boolean,
    deleteMessage: String?,
    onAdminPanel: () -> Unit,
    onSupport: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val repository = remember(context) { CampaignRepository(context) }
    var reputation by remember { mutableIntStateOf(0) }
    var reliability by remember { mutableStateOf<TesterReliability?>(null) }
    var allowGroupInvites by remember { mutableStateOf(true) }
    var invitePreferenceMessage by remember { mutableStateOf<String?>(null) }
    var profileName by remember(currentUser.id) { mutableStateOf(currentUser.displayName) }
    var profileBio by remember(currentUser.id) { mutableStateOf("") }
    var profileAvatar by remember(currentUser.id) { mutableStateOf("") }
    var contactEmail by remember(currentUser.id) { mutableStateOf("") }
    var showPublicEmail by remember(currentUser.id) { mutableStateOf(false) }
    var profileLoading by remember { mutableStateOf(true) }
    var profileSaving by remember { mutableStateOf(false) }
    var profileMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser.id) {
        reputation = withContext(Dispatchers.IO) { repository.myReputationScore().first }
        reliability = withContext(Dispatchers.IO) { repository.testerReliability().first }
        val preference = withContext(Dispatchers.IO) { repository.groupInvitePreference() }
        allowGroupInvites = preference.first
        invitePreferenceMessage = preference.second
        val profile = withContext(Dispatchers.IO) { repository.myProfile() }
        profile.first?.let {
            profileName = it.displayName.ifBlank { currentUser.displayName }
            profileBio = it.bio
            profileAvatar = it.avatarUrl
            contactEmail = it.contactEmail
            showPublicEmail = it.showPublicEmail
        }
        profileMessage = profile.second
        profileLoading = false
    }

    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        Text("HESAP", color = Color(0xFF2EE6A8), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
        Text(profileName.ifBlank { currentUser.displayName }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 10.dp))
        Text("Hesap e-postası · ${currentUser.email}", color = Color(0xFF9AA3B8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Text("İtibar puanın: $reputation", color = Color(0xFF2EE6A8), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141B33))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROFİLİNİ DÜZENLE", color = Color(0xFF2EE6A8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Toplum bölümünde diğer kullanıcıların göreceği bilgileri buradan yönetebilirsin.", color = Color(0xFF9AA3B8), fontSize = 12.sp)
                if (profileLoading) CircularProgressIndicator(color = Color(0xFF2EE6A8))
                else {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(50) },
                        label = { Text("Görünen ad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileBio,
                        onValueChange = { profileBio = it.take(280) },
                        label = { Text("Hakkımda") },
                        placeholder = { Text("Kendini kısaca tanıt…") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it.take(254) },
                        label = { Text("İletişim e-postası / Gmail") },
                        placeholder = { Text("ornek@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Bu alan hesap giriş e-postandan ayrıdır. İstersen farklı bir Gmail yazabilir, halka açık paylaşımı ayrıca açabilirsin.",
                        color = Color(0xFF9AA3B8),
                        fontSize = 11.sp
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Halka açık göster", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                            Text(
                                if (showPublicEmail) "İletişim e-postan Toplum bölümünde görünür." else "E-posta adresin diğer kullanıcılardan gizli kalır.",
                                color = Color(0xFF9AA3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = showPublicEmail, onCheckedChange = { showPublicEmail = it })
                    }
                    OutlinedTextField(
                        value = profileAvatar,
                        onValueChange = { profileAvatar = it.take(1000) },
                        label = { Text("Profil fotoğrafı bağlantısı (isteğe bağlı)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Profil fotoğrafı için doğrudan https:// ile başlayan bir görsel bağlantısı kullanabilirsin.", color = Color(0xFF9AA3B8), fontSize = 10.sp)
                    Button(
                        onClick = {
                            scope.launch {
                                profileSaving = true
                                val error = withContext(Dispatchers.IO) { repository.saveMyProfile(profileName, profileBio, profileAvatar, contactEmail, showPublicEmail) }
                                profileMessage = error ?: "Profilin kaydedildi."
                                profileSaving = false
                            }
                        },
                        enabled = !profileSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B8CFF), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (profileSaving) "Kaydediliyor…" else "Profili kaydet")
                    }
                }
                profileMessage?.let {
                    Text(
                        text = it,
                        color = if (it == "Profilin kaydedildi.") Color(0xFF2EE6A8) else MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141B33))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("TESTER GÜVENİLİRLİK PROFİLİ", color = Color(0xFF2EE6A8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Katıldığın test: ${reliability?.testsJoined ?: 0} · Tamamlanan: ${reliability?.testsCompleted ?: 0}", fontSize = 13.sp, color = Color.White)
                Text("Devamsızlık nedeniyle ayrıldığın: ${reliability?.testsDropped ?: 0}", fontSize = 12.sp, color = Color(0xFF9AA3B8))
                Text("Günlük test bildirimleri: ${reliability?.dailyCheckins ?: 0} · Kaçırılan gün: ${reliability?.missedDays ?: 0}", fontSize = 12.sp, color = Color(0xFF9AA3B8))
                Text("Katılım güvenilirliği: ${reliability?.attendancePercent ?: 100}%", fontSize = 13.sp, color = Color(0xFF2EE6A8), fontWeight = FontWeight.SemiBold)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141B33))
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Grup davetleri", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        if (allowGroupInvites) "Diğer kullanıcılar seni gruplarına davet edebilir." else "Grup davetleri kapalı. Toplum bölümünde davet alamazsın.",
                        color = Color(0xFF9AA3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(checked = allowGroupInvites, onCheckedChange = { enabled ->
                    scope.launch {
                        val error = withContext(Dispatchers.IO) { repository.setGroupInvitePreference(enabled) }
                        if (error == null) allowGroupInvites = enabled
                        invitePreferenceMessage = error ?: "Davet tercihin kaydedildi."
                    }
                })
            }
            invitePreferenceMessage?.let {
                Text(it, color = Color(0xFF9AA3B8), fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
            }
        }

        Text(
            "Tek hesapla tester ve geliştirici olarak yer alabilirsin. Hesabını silersen profilin ve ilişkili kişisel kayıtların veritabanından kaldırılır.",
            color = Color(0xFF9AA3B8),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        deleteMessage?.let { Text(it, color = Color(0xFFFF7B86), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onSupport,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141B33), contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Destek taleplerim")
        }

        TextButton(onClick = { uriHandler.openUri(CampaignRepository.TERMS_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text("Kullanım koşulları ve topluluk kuralları", color = Color(0xFF8B8CFF))
        }
        TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/privacy") }, modifier = Modifier.fillMaxWidth()) {
            Text("Gizlilik politikası", color = Color(0xFF8B8CFF))
        }
        TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/account-deletion") }, modifier = Modifier.fillMaxWidth()) {
            Text("Hesap ve veri silme sayfası", color = Color(0xFF8B8CFF))
        }
        if (isAdmin) {
            Button(
                onClick = onAdminPanel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EE6A8), contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Yönetici paneli", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141B33), contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Güvenli çıkış yap")
        }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Hesabımı ve verilerimi sil", color = Color(0xFFFF7B86))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hesap kalıcı olarak silinsin mi?") },
            text = { Text("Profilin, kampanya kayıtların ve hesabına bağlı kişisel verilerin silinir. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteAccount() }) {
                    Text("Kalıcı olarak sil", color = Color(0xFFFF7B86))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            }
        )
    }
}
