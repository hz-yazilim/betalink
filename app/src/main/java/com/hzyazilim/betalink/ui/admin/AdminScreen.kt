package com.hzyazilim.betalink.ui.admin

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
import com.hzyazilim.betalink.core.admin.AdminApiRepository
import com.hzyazilim.betalink.core.admin.AdminDashboard
import com.hzyazilim.betalink.core.admin.AdminReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { AdminApiRepository(context) }
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<AdminDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showAnnouncement by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) { repository.dashboard() }
        dashboard = result.first
        message = result.second
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("BETALINK", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                Text("Yönetici paneli", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = onClose) { Text("Kapat") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
        dashboard?.let { data ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Kullanıcı", data.users.toString(), Modifier.weight(1f))
                StatCard("Kampanya", data.campaigns.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StatCard("Şikâyet", data.reports.toString(), Modifier.weight(1f))
                StatCard("Grup", data.groups.toString(), Modifier.weight(1f))
            }
            Button(onClick = { showAnnouncement = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Tüm kullanıcılara duyuru gönder")
            }
            Text("Bekleyen şikâyetler", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
            if (data.pendingReports.isEmpty()) Text("Bekleyen şikâyet yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(data.pendingReports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        onResolve = { status ->
                            scope.launch {
                                val error = withContext(Dispatchers.IO) { repository.resolveReport(report.id, status) }
                                message = error ?: "Şikâyet güncellendi."
                                if (error == null) refresh++
                            }
                        },
                        onBlock = {
                            report.targetUserId?.let { target ->
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { repository.blockUser(target) }
                                    message = error ?: "Kullanıcı platform düzeyinde engellendi."
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAnnouncement) {
        AnnouncementDialog(
            onDismiss = { showAnnouncement = false },
            onSend = { title, body ->
                scope.launch {
                    val error = withContext(Dispatchers.IO) { repository.announce(title, body) }
                    message = error ?: "Duyuru tüm kullanıcılara gönderildi."
                    if (error == null) showAnnouncement = false
                }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ReportCard(report: AdminReport, onResolve: (String) -> Unit, onBlock: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(report.reason, style = MaterialTheme.typography.titleSmall)
            Text(report.details, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("Durum: ${report.status}", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
            Row {
                TextButton(onClick = { onResolve("reviewing") }) { Text("İncele") }
                TextButton(onClick = { onResolve("resolved") }) { Text("Çözüldü") }
                TextButton(onClick = { onResolve("rejected") }) { Text("Reddet") }
            }
            if (report.targetUserId != null) {
                TextButton(onClick = onBlock) { Text("Bildirilen kullanıcıyı engelle") }
            }
        }
    }
}

@Composable
private fun AnnouncementDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Platform duyurusu") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("Mesaj") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.trim().length >= 2 && body.trim().length >= 2) onSend(title, body) }) { Text("Gönder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
