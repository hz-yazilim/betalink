package com.hzyazilim.betalink.ui.groups

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
import com.hzyazilim.betalink.core.data.CampaignRepository
import com.hzyazilim.betalink.ui.wallet.WalletStatusCard
import com.hzyazilim.betalink.core.data.TesterGroup
import com.hzyazilim.betalink.core.data.GroupMember
import com.hzyazilim.betalink.core.data.GroupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GroupsScreen(onOpenContacts: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<TesterGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var selectedGroup by remember { mutableStateOf<TesterGroup?>(null) }
    var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var testGroup by remember { mutableStateOf<TesterGroup?>(null) }
    var groupSnapshot by remember { mutableStateOf<GroupSnapshot?>(null) }
    var shareEmail by remember { mutableStateOf(false) }
    var playTestEmail by remember { mutableStateOf("") }
    var snapshotLoading by remember { mutableStateOf(false) }
    var groupCheckins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupSearch by remember { mutableStateOf("") }

    LaunchedEffect(refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) { repository.listGroups() }
        groups = result.first
        message = result.second
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("TOPLULUK", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Text("Tester grupları", style = MaterialTheme.typography.headlineSmall)
        Text("Açık tester gruplarını görüntüle ve katıl.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        WalletStatusCard()
        Text("Grup oluşturma: 30 BP · Açık gruba katılım: 5 BP. İşlemler için yalnızca yeterli BP bakiyesi gerekir.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("＋ Yeni grup oluştur · 30 BP")
        }
        OutlinedTextField(
            value = groupSearch,
            onValueChange = { groupSearch = it },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Grup ara") },
            placeholder = { Text("Grup adı veya açıklama") },
            singleLine = true
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (groups.isEmpty()) Text("Henüz grup yok. İlk tester grubunu sen oluşturabilirsin.", modifier = Modifier.padding(top = 18.dp))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
            items(groups.filter { it.name.contains(groupSearch, ignoreCase = true) || it.description.contains(groupSearch, ignoreCase = true) }, key = { it.id }) { group ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(15.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Text("Herkese açık grup", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                        if (group.description.isNotBlank()) Text(group.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        if (group.isCreator) {
                            Row {
                                TextButton(onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { repository.groupMembers(group.id) }
                                        members = result.first
                                        message = result.second
                                        selectedGroup = group
                                    }
                                }) { Text("Üyeleri yönet") }
                                TextButton(onClick = onOpenContacts) { Text("Kişileri davet et") }
                            }
                        }
                        when (group.membershipStatus) {
                            "approved" -> {
                                Text("Grup üyesisin", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
                                TextButton(onClick = {
                                    scope.launch {
                                        snapshotLoading = true
                                        val result = withContext(Dispatchers.IO) { repository.groupSnapshot(group.id) }
                                        groupSnapshot = result.first
                                        groupCheckins = withContext(Dispatchers.IO) { repository.groupCheckinDates(group.id) }.first
                                        shareEmail = result.first?.myEmailShared ?: false
                                        playTestEmail = result.first?.myPlayTestEmail ?: ""
                                        message = result.second
                                        testGroup = if (result.first != null) group else null
                                        snapshotLoading = false
                                    }
                                }) { Text("14 günlük test süreci ve tester e-postaları") }
                            }
                            "pending" -> Text("Katılım isteğin bekliyor", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                            else -> Button(onClick = {
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { repository.joinGroup(group) }
                                    message = error ?: "Gruba katıldın."
                                    if (error == null) refresh++
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("Gruba katıl · 5 BP")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text("${group.name} · Üyeler") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (members.isEmpty()) item { Text("Henüz üye isteği yok.") }
                    items(members, key = { it.id }) { member ->
                        Column {
                            Text("Grup üyesi")
                            Text("Durum: ${member.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            if (member.status == "approved") {
                                TextButton(onClick = {
                                    scope.launch {
                                        val error = withContext(Dispatchers.IO) { repository.removeGroupMember(group.id, member.id) }
                                        message = error ?: "Üye çıkarıldı; bekleyen ilk tester kontenjana alındı."
                                        if (error == null) {
                                            members = withContext(Dispatchers.IO) { repository.groupMembers(group.id) }.first
                                            refresh++
                                        }
                                    }
                                }) { Text("Üyeyi çıkar", color = MaterialTheme.colorScheme.error) }
                            }
                            if (member.status == "pending") {
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewGroupMember(member.id, true) }
                                            message = error ?: "Üyelik onaylandı."
                                            if (error == null) members = withContext(Dispatchers.IO) { repository.groupMembers(group.id) }.first
                                        }
                                    }) { Text("Onayla") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            val error = withContext(Dispatchers.IO) { repository.reviewGroupMember(member.id, false) }
                                            message = error ?: "İstek reddedildi."
                                            if (error == null) members = withContext(Dispatchers.IO) { repository.groupMembers(group.id) }.first
                                        }
                                    }) { Text("Reddet") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedGroup = null }) { Text("Kapat") } }
        )
    }

    if (testGroup != null) {
        val group = testGroup!!
        val snapshot = groupSnapshot
        AlertDialog(
            onDismissRequest = { testGroup = null },
            title = { Text(group.name + " · Test süreci") },
            text = {
                Column {
                    if (snapshotLoading || snapshot == null) {
                        CircularProgressIndicator()
                    } else {
                        val statusText = when (snapshot.status) {
                            "running" -> "Test süreci başladı"
                            "completed" -> "14 günlük süreç tamamlandı"
                            "paused" -> "Üye sayısı eksildi · Süre duraklatıldı"
                            else -> "15 tester bekleniyor"
                        }
                        Text(statusText, color = MaterialTheme.colorScheme.secondary)
                        Text("${snapshot.approvedMembers}/${snapshot.requiredMembers} onaylı tester · ${(snapshot.approvedMembers * 100 / snapshot.requiredMembers.coerceAtLeast(1)).coerceIn(0,100)}% doluluk")
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { snapshot.approvedMembers.toFloat() / snapshot.requiredMembers.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${snapshot.elapsedDays}/${snapshot.requiredDays} gün tamamlandı")
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { snapshot.elapsedDays.toFloat() / snapshot.requiredDays.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val todayUtc = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
                        val checkedToday = todayUtc in groupCheckins
                        if (snapshot.status == "running") {
                            Text(if (checkedToday) "Bugünkü test bildirimin tamamlandı." else "Bugün uygulamayı test ettiğini bildir.")
                            Button(
                                onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { repository.checkInGroupToday(group.id) }
                                        message = result.second ?: if (result.first == true) "Bugünkü grup testi bildirimin kaydedildi." else "Bugünkü bildirimin zaten kayıtlı."
                                        if (result.second == null) {
                                            groupCheckins = withContext(Dispatchers.IO) { repository.groupCheckinDates(group.id) }.first
                                        }
                                    }
                                },
                                enabled = !checkedToday,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) { Text(if (checkedToday) "Bugün bildirildi ✓" else "Bugünkü testi tamamladım") }
                            if (groupCheckins.isNotEmpty()) {
                                Text("Son bildirimlerin: " + groupCheckins.sortedDescending().take(7).joinToString(" · "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (snapshot.status == "waiting" || snapshot.status == "paused") {
                            Text("15 tester onaylandığında 14 günlük sayaç başlayacak. Üye sayısı 15'in altına düşerse sayaç durur ve 15 tester yeniden tamamlandığında 14 gün baştan başlar.", fontSize = 11.sp)
                        } else {
                            Text("Testte kullanılacak e-posta adresleri (yalnızca paylaşım izni veren üyeler):", fontSize = 12.sp)
                            if (snapshot.emails.isEmpty()) Text("Henüz paylaşım izni veren üye yok.", fontSize = 11.sp)
                            snapshot.emails.forEach { item ->
                                Text((item.displayName.ifBlank { "Tester" }) + " · " + item.email, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = playTestEmail, onValueChange = { playTestEmail = it },
                            label = { Text("Google Play test e-posta adresin") },
                            placeholder = { Text("ornek@gmail.com") }, singleLine = true,
                            enabled = shareEmail, modifier = Modifier.fillMaxWidth()
                        )
                        Row {
                            Checkbox(checked = shareEmail, onCheckedChange = { shareEmail = it })
                            Column(Modifier.padding(top = 8.dp)) {
                                Text("Google Play test e-posta adresimi bu grubun onaylı üyeleriyle paylaş", fontSize = 12.sp)
                                Text("İstediğin zaman izni geri alabilirsin.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { repository.setGroupEmailConsent(group.id, shareEmail, playTestEmail) }
                                message = error ?: "E-posta paylaşım tercihin kaydedildi."
                                if (error == null) {
                                    val result = withContext(Dispatchers.IO) { repository.groupSnapshot(group.id) }
                                    groupSnapshot = result.first
                                    shareEmail = result.first?.myEmailShared ?: shareEmail
                                    playTestEmail = result.first?.myPlayTestEmail ?: playTestEmail
                                }
                            }
                        }) { Text("Paylaşım iznini kaydet") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { testGroup = null }) { Text("Kapat") } }
        )
    }

    if (showCreate) {
        CreateGroupDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, description, _ ->
                scope.launch {
                    val error = withContext(Dispatchers.IO) { repository.createGroup(name, description, false) }
                    message = error ?: "Grubun oluşturuldu."
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
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni tester grubu") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Grup adı") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Açıklama") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.trim().length >= 2) onCreate(name, description, false) }) { Text("Oluştur") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
