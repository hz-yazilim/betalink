package com.hzyazilim.betalink.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.hzyazilim.betalink.core.support.SupportRepository
import com.hzyazilim.betalink.core.support.SupportTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { SupportRepository(context) }
    val scope = rememberCoroutineScope()
    var tickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showNewTicket by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) { repository.listTickets() }
        tickets = result.first
        message = result.second
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Destek taleplerim") },
        text = {
            Column {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Button(onClick = { showNewTicket = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text("Yeni destek talebi / şikayet") }
                if (loading) CircularProgressIndicator()
                else if (tickets.isEmpty() && message == null) Text("Henüz destek talebin yok.", modifier = Modifier.padding(top = 10.dp))
                else if (tickets.isEmpty()) TextButton(onClick = { refresh++ }) { Text("Talepler yüklenemedi · Tekrar dene") }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tickets, key = { it.id }) { ticket ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(ticket.subject, style = MaterialTheme.typography.titleSmall)
                                Text("Durum: ${ticket.status}", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                                Text(ticket.message, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                ticket.adminReply?.let {
                                    Text("Destek yanıtı: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )

    if (showNewTicket) {
        NewSupportTicketDialog(
            onDismiss = { if (!submitting) showNewTicket = false },
            isSubmitting = submitting,
            onSubmit = { subject, body, ticketType ->
                scope.launch {
                    if (submitting) return@launch
                    submitting = true
                    try {
                        val error = withContext(Dispatchers.IO) { repository.createTicket(subject, body, ticketType) }
                        message = error ?: "Destek talebin oluşturuldu."
                        if (error == null) {
                            showNewTicket = false
                            refresh++
                        }
                    } finally {
                        submitting = false
                    }
                }
            }
        )
    }
}

@Composable
private fun NewSupportTicketDialog(onDismiss: () -> Unit, isSubmitting: Boolean, onSubmit: (String, String, String) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var ticketType by remember { mutableStateOf("support") }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(if (ticketType == "complaint") "Yeni şikayet" else "Yeni destek talebi") },
        text = {
            Column {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { ticketType = "support" }) { Text(if (ticketType == "support") "✓ Destek" else "Destek") }
                    TextButton(onClick = { ticketType = "complaint" }) { Text(if (ticketType == "complaint") "✓ Şikayet" else "Şikayet") }
                }
                OutlinedTextField(subject, { subject = it }, label = { Text("Konu") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("Mesajın") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(enabled = !isSubmitting && subject.trim().length in 3..120 && body.trim().length in 5..5000, onClick = { onSubmit(subject, body, ticketType) }) { Text(if (isSubmitting) "Gönderiliyor…" else "Gönder") }
        },
        dismissButton = { TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Vazgeç") } }
    )
}
