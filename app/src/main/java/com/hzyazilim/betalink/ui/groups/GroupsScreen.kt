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
import com.hzyazilim.betalink.core.data.TesterGroup
import com.hzyazilim.betalink.core.data.GroupMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GroupsScreen() {
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
        Text("Açık gruplara katıl veya özel gruplara katılım isteği gönder.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("＋ Yeni grup oluştur")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (groups.isEmpty()) Text("Henüz grup yok. İlk tester grubunu sen oluşturabilirsin.", modifier = Modifier.padding(top = 18.dp))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
            items(groups, key = { it.id }) { group ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(15.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (group.isPrivate) "Özel grup" else "Açık grup", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                        if (group.description.isNotBlank()) Text(group.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        if (group.isCreator) {
                            TextButton(onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { repository.groupMembers(group.id) }
                                    members = result.first
                                    message = result.second
                                    selectedGroup = group
                                }
                            }) { Text("Üye isteklerini yönet") }
                        }
                        when (group.membershipStatus) {
                            "approved" -> Text("Grup üyesisin", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
                            "pending" -> Text("Katılım isteğin bekliyor", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                            else -> Button(onClick = {
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { repository.joinGroup(group) }
                                    message = error ?: if (group.isPrivate) "Katılım isteğin gönderildi." else "Gruba katıldın."
                                    if (error == null) refresh++
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(if (group.isPrivate) "Katılım isteği gönder" else "Gruba katıl")
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
                            Text("Üye: ${member.userId.take(8)}…")
                            Text("Durum: ${member.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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

    if (showCreate) {
        CreateGroupDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, description, privateGroup ->
                scope.launch {
                    val error = withContext(Dispatchers.IO) { repository.createGroup(name, description, privateGroup) }
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
    var privateGroup by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni tester grubu") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Grup adı") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Açıklama") }, minLines = 2)
                Row {
                    Checkbox(checked = privateGroup, onCheckedChange = { privateGroup = it })
                    Text("Özel grup (onay gerekir)", modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.trim().length >= 2) onCreate(name, description, privateGroup) }) { Text("Oluştur") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
