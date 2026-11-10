package com.hzyazilim.betalink.ui.people

import android.content.Intent
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
import com.hzyazilim.betalink.core.data.DirectoryPerson
import com.hzyazilim.betalink.core.data.GroupInvitation
import com.hzyazilim.betalink.core.data.TesterGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PeopleScreen(inviteToken: String? = null) {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<DirectoryPerson>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<GroupInvitation>>(emptyList()) }
    var myGroups by remember { mutableStateOf<List<TesterGroup>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<TesterGroup?>(null) }
    var personToInvite by remember { mutableStateOf<DirectoryPerson?>(null) }
    var customMessage by remember { mutableStateOf("") }
    var showGroupPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var canLoadMore by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        val directory = withContext(Dispatchers.IO) { repository.listPeople(0) }
        val inbox = withContext(Dispatchers.IO) { repository.listIncomingGroupInvitations(inviteToken) }
        val groups = withContext(Dispatchers.IO) { repository.listGroups() }
        people = directory.first
        canLoadMore = directory.first.size == 50
        invitations = inbox.first
        myGroups = groups.first.filter { it.isCreator }
        message = directory.second ?: inbox.second ?: groups.second
        if (selectedGroup == null) selectedGroup = myGroups.firstOrNull()
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("TOPLULUK", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Text("Kişiler", style = MaterialTheme.typography.headlineSmall)
        Text("Betalink'e kayıtlı kişileri keşfet, gruplarına davet gönder ve gelen davetleri yönet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }

        if (loading) {
            CircularProgressIndicator(Modifier.padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text("Gelen grup davetleri · ${invitations.size}", style = MaterialTheme.typography.titleMedium)
                }
                if (invitations.isEmpty()) item {
                    Text("Bekleyen grup davetin yok.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                items(invitations, key = { it.id }) { invite ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp)) {
                            Text(invite.groupName, style = MaterialTheme.typography.titleSmall)
                            if (invite.groupDescription.isNotBlank()) Text(invite.groupDescription, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (invite.message.isNotBlank()) Text("Not: ${invite.message}", fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true
                                        val error = withContext(Dispatchers.IO) { repository.respondGroupInvitation(invite.id, true) }
                                        message = error ?: "Davet kabul edildi. Gruba katıldın."
                                        busy = false
                                        if (error == null) refresh++
                                    }
                                }) { Text("Kabul et") }
                                TextButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true
                                        val error = withContext(Dispatchers.IO) { repository.respondGroupInvitation(invite.id, false) }
                                        message = error ?: "Davet reddedildi."
                                        busy = false
                                        if (error == null) refresh++
                                    }
                                }) { Text("Reddet") }
                            }
                        }
                    }
                }

                item {
                    Text("Davet göndereceğin grup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    if (myGroups.isEmpty()) {
                        Text("Önce Gruplar bölümünden bir tester grubu oluşturmalısın.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = { showGroupPicker = true }) {
                            Text(selectedGroup?.name ?: "Grup seç")
                        }
                    }
                    Text("Uygulamadaki kişiler · ${people.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(people, key = { it.id }) { person ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(if (person.canReceiveInvites) "Grup daveti alabilir" else "Davet almayı kapatmış", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            TextButton(
                                enabled = person.canReceiveInvites && selectedGroup != null && !busy,
                                onClick = { personToInvite = person }
                            ) { Text("Davet et") }
                        }
                    }
                }
                if (canLoadMore) item {
                    TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            val next = withContext(Dispatchers.IO) { repository.listPeople(people.size) }
                            message = next.second
                            people = people + next.first
                            canLoadMore = next.first.size == 50
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Daha fazla kişi yükle") }
                }
                item {
                    Text("E-posta adresleri diğer kullanıcılara gösterilmez. Grup davetlerini kapatan kişiler davet alamaz.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }

    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("Davet gönderilecek grup") },
            text = {
                Column {
                    myGroups.forEach { group ->
                        TextButton(onClick = { selectedGroup = group; showGroupPicker = false }) {
                            Text(group.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupPicker = false }) { Text("Kapat") } }
        )
    }

    if (personToInvite != null) {
        val person = personToInvite!!
        AlertDialog(
            onDismissRequest = { if (!busy) personToInvite = null },
            title = { Text("${person.displayName} kişisini davet et") },
            text = {
                Column {
                    Text("Grup: ${selectedGroup?.name ?: "Seçilmedi"}")
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it.take(500) },
                        label = { Text("Davet notu (isteğe bağlı)") },
                        placeholder = { Text("Grubun amacı ve katılımcılardan beklentiler…") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && selectedGroup != null, onClick = {
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) {
                            repository.sendGroupInvitation(selectedGroup!!.id, person.id, customMessage)
                        }
                        val error = result.second
                        message = error ?: "Davet gönderildi. Paylaşım menüsünden grup bilgilerini de iletebilirsin."
                        busy = false
                        if (error == null) {
                            val group = selectedGroup!!
                            val shareText = buildString {
                                append("Betalink grup daveti\n\n")
                                append("${group.name}\n")
                                if (group.description.isNotBlank()) append("${group.description}\n")
                                if (customMessage.isNotBlank()) append("\nDavet notu: ${customMessage}\n")
                                append("\nBetalink; uygulama geliştiricileri ile gerçek testerları buluşturan bir platformdur. Gruplar üzerinden kapalı testlere birlikte katılabilir, test sürecini ve günlük katılımı takip edebilirsin.")
                                append("\n\nDavet bağlantısı: betalink://group-invite/${result.first ?: ""}")
                                append("\n\nUygulama: https://play.google.com/store/apps/details?id=com.hzyazilim.betalink")
                                append("\nDavetini uygulamadaki Kişiler > Gelen grup davetleri bölümünden kabul edebilirsin.")
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }, "Betalink grup davetini paylaş"))
                            }
                            personToInvite = null
                            customMessage = ""
                            refresh++
                        }
                    }
                }) { Text(if (busy) "Gönderiliyor…" else "Davet gönder ve paylaş") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { personToInvite = null }) { Text("Vazgeç") } }
        )
    }
}
