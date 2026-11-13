package com.hzyazilim.betalink.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.hzyazilim.betalink.core.data.BetalinkNotification
import com.hzyazilim.betalink.core.data.CampaignRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NotificationsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<BetalinkNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var updatingNotificationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        val result = withContext(Dispatchers.IO) { repository.listNotifications() }
        notifications = result.first
        message = result.second
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bildirimler") },
        text = {
            Column {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (loading) CircularProgressIndicator()
                else if (notifications.isEmpty()) Text("Henüz bildirimin yok.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notifications, key = { it.id }) { notification ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(notification.title, style = MaterialTheme.typography.titleSmall)
                            Text(notification.body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Row {
                                Text(if (notification.read) "Okundu" else "Yeni", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                                if (!notification.read) {
                                    TextButton(enabled = updatingNotificationId == null, onClick = {
                                        scope.launch {
                                            if (updatingNotificationId != null) return@launch
                                            updatingNotificationId = notification.id
                                            try {
                                                val error = withContext(Dispatchers.IO) { repository.markNotificationRead(notification.id) }
                                                message = error
                                                if (error == null) refresh++
                                            } finally {
                                                updatingNotificationId = null
                                            }
                                        }
                                    }) { Text(if (updatingNotificationId == notification.id) "Kaydediliyor…" else "Okundu işaretle") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}
