package com.hzyazilim.betalink

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.hzyazilim.betalink.ui.auth.AuthGate
import com.hzyazilim.betalink.core.auth.BetalinkUser
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    AppTab("Testlerim", Icons.Filled.Checklist),
    AppTab("Profil", Icons.Filled.Person)
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
                    AuthGate()
                }
            }
        }
    }
}

@Composable
fun BetalinkApp(currentUser: BetalinkUser, onSignOut: suspend () -> Unit, onDeleteAccount: suspend () -> String?) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var deleteMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF11192B)) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
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
            when (selectedTab) {
                0 -> HomeScreen(onExplore = { selectedTab = 1 }, onMyTests = { selectedTab = 3 })
                1 -> EmptyListScreen(
                    eyebrow = "KEŞFET",
                    title = "Sana uygun testleri bul",
                    message = "Cihazına ve Android sürümüne uygun açık kampanyalar burada listelenecek.",
                    action = "Filtreleri backend hazır olduğunda kullanabileceksin."
                )
                2 -> EmptyListScreen(
                    eyebrow = "TOPLULUK",
                    title = "Tester grupları",
                    message = "Açık gruplara katıl, özel gruplara davetle gir ve kampanyalar için birlikte organize ol.",
                    action = "Henüz gösterilecek grup yok."
                )
                3 -> EmptyListScreen(
                    eyebrow = "KONTROL PANELİ",
                    title = "Testlerin burada",
                    message = "Katıldığın kampanyalar, her kampanyanın 14 günlük süresi, görevleri ve yedek liste durumun burada takip edilecek.",
                    action = "Henüz katıldığın kampanya yok."
                )
                else -> ProfileScreen(currentUser, deleteMessage = deleteMessage, onSignOut = { scope.launch { onSignOut() } }, onDeleteAccount = { scope.launch { deleteMessage = onDeleteAccount() ?: "Hesap silme işlemi tamamlandı." } })
            }
        }
    }
}

@Composable
private fun HomeScreen(onExplore: () -> Unit, onMyTests: () -> Unit) {
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
                Column(Modifier.padding(start = 11.dp)) {
                    Text("BETALINK", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                    Text("Test et. Geri bildirim ver. Geliştir.", color = Color(0xFF9BA8C2), fontSize = 11.sp)
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
private fun ProfileScreen(currentUser: BetalinkUser, deleteMessage: String?, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("HESAP", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
        Text(currentUser.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(currentUser.email, color = Color(0xFFB5BED4), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Tek hesapla tester ve geliştirici olarak yer alabilirsin. Hesabını silersen profilin ve ilişkili kişisel kayıtların veritabanından kaldırılır.",
            color = Color(0xFFB5BED4), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
        )
        deleteMessage?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(18.dp))
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
