package com.hzyazilim.betalink.ui.people

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hzyazilim.betalink.core.data.CampaignRepository
import com.hzyazilim.betalink.core.data.CommunityPost
import com.hzyazilim.betalink.core.data.DirectoryPerson
import com.hzyazilim.betalink.core.data.GroupInvitation
import com.hzyazilim.betalink.core.data.PublicUserProfile
import com.hzyazilim.betalink.core.data.TesterGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val emailOnlyCommunityPost = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

private fun isCommunityPostBodyAllowed(body: String): Boolean =
    body.trim().isNotEmpty() && !emailOnlyCommunityPost.matches(body.trim())

@Composable
fun PeopleScreen(inviteToken: String? = null) {
    val context = LocalContext.current
    val repository = remember(context) { CampaignRepository(context) }
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<DirectoryPerson>>(emptyList()) }
    var communityPosts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var newPostText by remember { mutableStateOf("") }
    var postSubmitting by remember { mutableStateOf(false) }
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
    var blockedPeople by remember { mutableStateOf<List<DirectoryPerson>>(emptyList()) }
    var selectedPerson by remember { mutableStateOf<DirectoryPerson?>(null) }
    var selectedProfile by remember { mutableStateOf<PublicUserProfile?>(null) }
    var profileLoading by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var blockConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, inviteToken) {
        loading = true
        val directory = withContext(Dispatchers.IO) { repository.listPeople(0) }
        val blocked = withContext(Dispatchers.IO) { repository.listBlockedPeople() }
        val inbox = withContext(Dispatchers.IO) { repository.listIncomingGroupInvitations(inviteToken) }
        val groups = withContext(Dispatchers.IO) { repository.listGroups() }
        val feed = withContext(Dispatchers.IO) { repository.listCommunityPosts() }
        blockedPeople = blocked.first
        val blockedIds = blocked.first.map { it.id }.toSet()
        people = directory.first.filterNot { it.id in blockedIds }
        communityPosts = feed.first.filter { it.authorId !in blockedIds && isCommunityPostBodyAllowed(it.body) }
        canLoadMore = directory.first.size == 50
        invitations = inbox.first
        myGroups = groups.first.filter { it.isCreator }
        message = directory.second ?: blocked.second ?: inbox.second ?: groups.second ?: feed.second
        if (selectedGroup == null) selectedGroup = myGroups.firstOrNull()
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { refresh++ }) { Text("Yeniden dene") }
        }

        if (loading) {
            CircularProgressIndicator(Modifier.padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text("TOPLULUK AKIŞI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Test deneyimini, önerilerini ve geliştirici duyurularını paylaş.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(
                                value = newPostText,
                                onValueChange = { newPostText = it.take(280) },
                                label = { Text("Topluluğa bir şey yaz…") },
                                supportingText = { Text("${newPostText.length}/280") },
                                minLines = 2,
                                enabled = !postSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (postSubmitting) return@launch
                                        postSubmitting = true
                                        try {
                                            val error = withContext(Dispatchers.IO) { repository.createCommunityPost(newPostText) }
                                            message = error ?: "Paylaşımın yayınlandı."
                                            if (error == null) {
                                                newPostText = ""
                                                communityPosts = withContext(Dispatchers.IO) { repository.listCommunityPosts() }.first.filter { isCommunityPostBodyAllowed(it.body) }
                                            }
                                        } finally {
                                            postSubmitting = false
                                        }
                                    }
                                },
                                enabled = !postSubmitting && newPostText.trim().isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (postSubmitting) "Paylaşılıyor…" else "Paylaş") }
                        }
                    }
                }
                if (communityPosts.isEmpty()) item {
                    Text("Henüz paylaşım yok. İlk paylaşımı sen yapabilirsin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                items(communityPosts, key = { "post-${it.id}" }) { post ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!post.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = post.avatarUrl,
                                        contentDescription = "Paylaşım sahibi profil fotoğrafı",
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                                        Text(post.authorName.trim().firstOrNull()?.uppercase() ?: "B", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(post.authorName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(post.body, style = MaterialTheme.typography.bodyMedium)
                            Text(post.createdAt.replace("T", " ").take(16), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
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
                                        if (busy) return@launch
                                        busy = true
                                        try {
                                            val error = withContext(Dispatchers.IO) { repository.respondGroupInvitation(invite.id, true) }
                                            message = error ?: "Davet kabul edildi. Gruba katıldın."
                                            if (error == null) refresh++
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }) { Text("Kabul et") }
                                TextButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        if (busy) return@launch
                                        busy = true
                                        try {
                                            val error = withContext(Dispatchers.IO) { repository.respondGroupInvitation(invite.id, false) }
                                            message = error ?: "Davet reddedildi."
                                            if (error == null) refresh++
                                        } finally {
                                            busy = false
                                        }
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
                    Text("Toplumdaki kullanıcılar · ${people.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(people, key = { it.id }) { person ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable {
                            selectedPerson = person
                            selectedProfile = null
                            profileError = null
                            profileLoading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { repository.publicUserProfile(person.id) }
                                selectedProfile = result.first
                                profileError = result.second
                                profileLoading = false
                            }
                        }.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!person.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = person.avatarUrl,
                                    contentDescription = "Kullanıcı profil fotoğrafı",
                                    modifier = Modifier.size(42.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(person.displayName.trim().firstOrNull()?.uppercase() ?: "B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                if (person.bio.isNotBlank()) Text(person.bio, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2)
                                if (person.publicEmail.isNotBlank()) Text(person.publicEmail, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                                Text(if (person.canReceiveInvites) "Grup daveti alabilir" else "Davet almayı kapatmış", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
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
                            if (busy) return@launch
                            busy = true
                            try {
                                val next = withContext(Dispatchers.IO) { repository.listPeople(people.size) }
                                message = next.second
                                if (next.second == null) {
                                    val blockedIds = blockedPeople.map { it.id }.toSet()
                                    people = people + next.first.filterNot { it.id in blockedIds }
                                    canLoadMore = next.first.size == 50
                                }
                            } finally {
                                busy = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Daha fazla kişi yükle") }
                }
                if (blockedPeople.isNotEmpty()) item {
                    Text("ENGELLEDİĞİN KULLANICILAR · ${blockedPeople.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                }
                items(blockedPeople, key = { "blocked-${it.id}" }) { blocked ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(blocked.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val error = withContext(Dispatchers.IO) { repository.unblockUser(blocked.id) }
                                        message = error ?: "${blocked.displayName} için engel kaldırıldı."
                                        if (error == null) refresh++
                                    } finally {
                                        busy = false
                                    }
                                }
                            }) { Text("Engeli kaldır") }
                        }
                    }
                }
                item {
                    Text("Yalnızca halka açık paylaşımı açanların iletişim e-postası görünür. Hesap giriş e-postası gizli kalır. Grup davetlerini kapatan kişiler davet alamaz.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }

    selectedPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { if (!profileLoading && !blockConfirmation) selectedPerson = null },
            title = { Text(person.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (profileLoading) {
                        CircularProgressIndicator()
                    } else if (selectedProfile != null) {
                        val profile = selectedProfile!!
                        if (profile.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = "Profil fotoğrafı",
                                modifier = Modifier.size(76.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (profile.bio.isNotBlank()) Text(profile.bio, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (profile.sameGroup) {
                            Text("✓ Aynı test grubundasınız", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                        }
                        Text("İtibar puanı: ${profile.reputationPoints}", fontWeight = FontWeight.SemiBold)
                        Text("BetaPuan: ${profile.betaPoints}", color = MaterialTheme.colorScheme.secondary)
                        Text("Tamamlanan test: ${profile.testsCompleted}")
                        Text("Katıldığı test: ${profile.testsJoined} · Ayrıldığı: ${profile.testsDropped}")
                        Text("Günlük katılım: %${profile.attendancePercent} · Bildirim: ${profile.dailyCheckins} · Kaçırılan gün: ${profile.missedDays}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(profileError ?: "Profil bilgileri yüklenemedi.", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    personToInvite = person
                    selectedPerson = null
                }, enabled = person.canReceiveInvites && selectedGroup != null) { Text("Gruba davet et") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { selectedPerson = null }) { Text("Kapat") }
                    TextButton(
                        enabled = !busy && !profileLoading,
                        onClick = { blockConfirmation = true }
                    ) { Text("Engelle", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    if (blockConfirmation) {
        AlertDialog(
            onDismissRequest = { blockConfirmation = false },
            title = { Text("Kullanıcı engellensin mi?") },
            text = { Text("Bu kullanıcıyı engellediğinde Toplum listesinden kaldırılır ve sana grup daveti gönderemez. Engeli daha sonra Engellediğin Kullanıcılar bölümünden kaldırabilirsin.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val person = selectedPerson ?: return@TextButton
                    scope.launch {
                        busy = true
                        try {
                            val error = withContext(Dispatchers.IO) { repository.blockUser(person.id) }
                            message = error ?: "${person.displayName} engellendi."
                            blockConfirmation = false
                            if (error == null) {
                                selectedPerson = null
                                refresh++
                            }
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Engelle", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { blockConfirmation = false }) { Text("Vazgeç") } }
        )
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
                        if (busy) return@launch
                        val group = selectedGroup ?: return@launch
                        busy = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                repository.sendGroupInvitation(group.id, person.id, customMessage)
                            }
                            val error = result.second
                            message = error ?: "Davet gönderildi. Paylaşım menüsünden grup bilgilerini de iletebilirsin."
                            if (error == null) {
                            val shareText = buildString {
                                append("Betalink grup daveti\n\n")
                                append("${group.name}\n")
                                if (group.description.isNotBlank()) append("${group.description}\n")
                                if (customMessage.isNotBlank()) append("\nDavet notu: ${customMessage}\n")
                                append("\nBetalink; uygulama geliştiricileri ile gerçek testerları buluşturan bir platformdur. Gruplar üzerinden kapalı testlere birlikte katılabilir, test sürecini ve günlük katılımı takip edebilirsin.")
                                append("\n\nDavet bağlantısı: betalink://group-invite/${result.first ?: ""}")
                                append("\n\nUygulama: https://play.google.com/store/apps/details?id=com.hzyazilim.betalink")
                                append("\nDavetini uygulamadaki Toplum > Gelen grup davetleri bölümünden kabul edebilirsin.")
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
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "Gönderiliyor…" else "Davet gönder ve paylaş") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { personToInvite = null }) { Text("Vazgeç") } }
        )
    }
}
