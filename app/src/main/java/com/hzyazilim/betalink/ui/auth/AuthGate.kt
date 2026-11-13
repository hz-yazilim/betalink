package com.hzyazilim.betalink.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.hzyazilim.betalink.BuildConfig
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.hzyazilim.betalink.BetalinkApp
import com.hzyazilim.betalink.core.auth.BetalinkUser
import com.hzyazilim.betalink.core.auth.SupabaseAuthRepository
import com.hzyazilim.betalink.core.auth.SessionRestoreResult
import com.hzyazilim.betalink.core.data.CampaignRepository
import com.hzyazilim.betalink.core.admin.AdminApiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthGate(initialInviteToken: String? = null, authCallbackUri: String? = null) {
    val context = LocalContext.current
    val repository = remember { SupabaseAuthRepository(context) }
    var user by remember { mutableStateOf<BetalinkUser?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var restoreRetryable by remember { mutableStateOf(false) }
    var continueToLogin by remember { mutableStateOf(false) }
    var restoreAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(restoreAttempt, authCallbackUri) {
        checking = true
        // An email-confirmation callback carries a newer, verified session.
        // Skip restoring an older local session so it cannot overwrite the callback.
        if (!authCallbackUri.isNullOrBlank()) {
            checking = false
            return@LaunchedEffect
        }
        // A temporary network outage must not erase the encrypted refresh token
        // or force the user to sign in again.
        val result = try {
            withContext(Dispatchers.IO) { repository.restoreSession() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SessionRestoreResult(
                message = "Oturum kontrol edilemedi. İnternet bağlantını kontrol edip tekrar dene.",
                retryable = true
            )
        }
        user = result.user
        authMessage = result.message
        restoreRetryable = result.retryable
        checking = false
    }
    LaunchedEffect(authCallbackUri) {
        if (!authCallbackUri.isNullOrBlank()) {
            val result = try {
                withContext(Dispatchers.IO) { repository.completeEmailConfirmation(authCallbackUri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (result?.user != null) {
                user = result.user
                authMessage = "E-posta adresin doğrulandı. Betalink'e hoş geldin!"
            } else {
                authMessage = result?.message ?: "E-posta doğrulaması tamamlanamadı. Tekrar giriş yapmayı dene."
            }
        }
    }
    LaunchedEffect(user?.id) {
        if (user != null) {
            // These are background profile/permission checks. A transient API or
            // JSON parsing error should not crash the signed-in application.
            try {
                withContext(Dispatchers.IO) { CampaignRepository(context).syncTesterDevice() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Device profile sync is non-blocking; continue to the app.
            }
            isAdmin = try {
                withContext(Dispatchers.IO) { AdminApiRepository(context).isAdmin() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        } else isAdmin = false
    }
    if (checking) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Betalink hazırlanıyor…", modifier = Modifier.padding(top = 12.dp))
            }
        }
    } else if (user == null && restoreRetryable && !continueToLogin) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Oturum bağlantısı kurulamadı", style = MaterialTheme.typography.titleLarge)
                Text(
                    authMessage ?: "İnternet bağlantını kontrol edip tekrar dene.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Button(
                    onClick = { restoreAttempt++ },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                ) { Text("Tekrar dene") }
                TextButton(onClick = { continueToLogin = true }) { Text("Giriş ekranına geç") }
            }
        }
    } else if (user == null) {
        AuthScreen(
            onSignIn = { email, password, remember -> withContext(Dispatchers.IO) { repository.signIn(email, password, remember) } },
            onSignUp = { name, email, password -> withContext(Dispatchers.IO) { repository.signUp(email, password, name) } },
            onGoogleSignIn = { idToken, remember -> withContext(Dispatchers.IO) { repository.signInWithGoogleIdToken(idToken, remember) } },
            onResendConfirmation = { email -> withContext(Dispatchers.IO) { repository.resendSignupConfirmation(email) } },
            onPasswordReset = { email -> withContext(Dispatchers.IO) { repository.sendPasswordReset(email) } },
            initialMessage = authMessage,
            onAuthenticated = { user = it }
        )
    } else {
        BetalinkApp(
            currentUser = user!!,
            isAdmin = isAdmin,
            openPeopleInitially = !initialInviteToken.isNullOrBlank(),
            initialInviteToken = initialInviteToken,
            onSignOut = {
                withContext(Dispatchers.IO) { repository.signOut() }
                user = null
            },
            onDeleteAccount = {
                val error = withContext(Dispatchers.IO) { repository.deleteAccount() }
                if (error == null) user = null
                error
            }
        )
    }
}

@Composable
private fun AuthScreen(
    onSignIn: suspend (String, String, Boolean) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onSignUp: suspend (String, String, String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onGoogleSignIn: suspend (String, Boolean) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onResendConfirmation: suspend (String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onPasswordReset: suspend (String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    initialMessage: String?,
    onAuthenticated: (BetalinkUser) -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("dev") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(initialMessage) }
    var messageIsSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(initialMessage) { if (!initialMessage.isNullOrBlank()) message = initialMessage }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val googleClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.data != null) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                val token = account.idToken
                if (token.isNullOrBlank()) {
                    message = "Google kimlik jetonu alınamadı. OAuth Web Client ID'yi kontrol et."
                    messageIsSuccess = false
                } else {
                    scope.launch {
                        busy = true
                        message = null
                        val authResult = try {
                            onGoogleSignIn(token, rememberMe)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            com.hzyazilim.betalink.core.auth.AuthResult(message = "Google girişi tamamlanamadı. İnternet bağlantını kontrol edip tekrar dene.")
                        } finally {
                            busy = false
                        }
                        if (authResult.user != null) onAuthenticated(authResult.user)
                        else { message = authResult.message ?: "Google ile giriş tamamlanamadı."; messageIsSuccess = false }
                    }
                }
            } catch (error: ApiException) {
                message = when (error.statusCode) {
                    10 -> "Google girişi yapılandırma hatası (Kod 10: DEVELOPER_ERROR). Google Cloud projesindeki Android SHA-1 ve Web Client ID ayarlarını kontrol et."
                    12500 -> "Google hesabıyla oturum açılamadı. Google Play Hizmetlerini ve cihazdaki hesabı kontrol et."
                    12501 -> "Google giriş penceresi kapatıldı veya iptal edildi."
                    else -> "Google girişi başarısız oldu (kod: ${error.statusCode})."
                }
                messageIsSuccess = false
            }
        } else {
            message = "Google girişi iptal edildi."
            messageIsSuccess = false
        }
    }

    val pageBackground = Brush.verticalGradient(
        listOf(Color(0xFF07070F), Color(0xFF101027), Color(0xFF090A18))
    )
    val accent = Color(0xFF9B8CFF)
    val muted = Color(0xFFAAA9C0)

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF07070F)) {
        Box(
            modifier = Modifier.fillMaxSize().background(pageBackground)
        ) {
            Box(
                Modifier.align(Alignment.TopStart).size(240.dp).offset(x = (-115).dp, y = (-100).dp)
                    .clip(CircleShape).background(Color(0x556D5CFF))
            )
            Box(
                Modifier.align(Alignment.CenterEnd).size(210.dp).offset(x = 125.dp, y = (-40).dp)
                    .clip(CircleShape).background(Color(0x442E7CFF))
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.size(78.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFA99BFF), Color(0xFF5B4DFF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                }
                Text("BETALINK", color = Color.White, fontSize = 27.sp, letterSpacing = 5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                Text("Test et. Geri bildirim ver. Geliştir.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier.padding(top = 13.dp).clip(RoundedCornerShape(30.dp))
                        .background(Color(0x1AFFFFFF)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(30.dp))
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                    Text(" Gerçek test topluluğuna katıl", color = Color(0xFFE7E5F4), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
                        .clip(RoundedCornerShape(30.dp)).background(Color(0x17FFFFFF))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(30.dp)).padding(4.dp)
                ) {
                    listOf("dev" to "👨‍💻 Geliştirici", "tester" to "🧪 Tester").forEach { (role, label) ->
                        val active = selectedRole == role
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(30.dp))
                                .background(if (active) accent else Color.Transparent)
                                .clickable { selectedRole = role },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (active) Color(0xFF101020) else muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
                Text(
                    if (selectedRole == "dev") "Uygulamanı test ettir, geri bildirimleri yönet ve geliştir."
                    else "Yeni uygulamaları dene, görevleri tamamla ve geliştiricilere destek ol.",
                    color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 9.dp, bottom = 15.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF0141424)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2AFFFFFF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (isSignUp) "Aramıza katıl" else "Tekrar hoş geldin", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(
                            if (isSignUp) "Betalink hesabını birkaç adımda oluştur."
                            else "Kaldığın yerden güvenle devam et.",
                            color = muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp, bottom = 18.dp)
                        )

                        if (isSignUp) {
                            OutlinedTextField(
                                value = name, onValueChange = { name = it.take(50) },
                                label = { Text("Adın veya görünen adın") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedTextField(
                            value = email, onValueChange = { email = it.take(254) },
                            label = { Text("E-posta") }, placeholder = { Text("ornek@gmail.com") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("Şifre") }, placeholder = { Text("Şifreni gir") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(if (passwordVisible) "Gizle" else "Göster", color = accent, fontSize = 11.sp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )

                        if (!isSignUp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                                    Text("Beni hatırla", color = muted, fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        if (email.isBlank()) {
                                            message = "Şifre sıfırlama bağlantısı için önce e-posta adresini yaz."
                                            messageIsSuccess = false
                                        } else {
                                            scope.launch {
                                                busy = true
                                                message = null
                                                val result = try { onPasswordReset(email) }
                                                catch (cancelled: CancellationException) { throw cancelled }
                                                catch (_: Exception) { com.hzyazilim.betalink.core.auth.AuthResult(message = "Şifre sıfırlama e-postası gönderilemedi. Bağlantını kontrol edip tekrar dene.") }
                                                finally { busy = false }
                                                message = result.message
                                                messageIsSuccess = result.message?.contains("gönderildi", true) == true
                                            }
                                        }
                                    },
                                    enabled = !busy
                                ) { Text("Şifremi unuttum", color = accent, fontSize = 11.sp) }
                            }
                        }

                        message?.let {
                            Text(
                                it,
                                color = if (messageIsSuccess) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (busy) return@Button
                                val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
                                val passwordValid = if (isSignUp) password.length >= 12 &&
                                    password.any(Char::isUpperCase) && password.any(Char::isLowerCase) &&
                                    password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() }
                                else password.isNotBlank()
                                if (!emailValid || !passwordValid || (isSignUp && name.isBlank())) {
                                    message = if (isSignUp)
                                        "Geçerli e-posta ve ad gir. Şifre en az 12 karakter; büyük/küçük harf, rakam ve özel karakter içermeli."
                                    else "Geçerli e-posta adresini ve şifreni gir."
                                    messageIsSuccess = false
                                } else {
                                    scope.launch {
                                        busy = true
                                        message = null
                                        val result = try {
                                            if (isSignUp) onSignUp(name, email, password)
                                            else onSignIn(email, password, rememberMe)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            com.hzyazilim.betalink.core.auth.AuthResult(message = "İşlem tamamlanamadı. İnternet bağlantını kontrol edip tekrar dene.")
                                        } finally {
                                            busy = false
                                        }
                                        if (result.user != null) onAuthenticated(result.user)
                                        else { message = result.message ?: "İşlem tamamlanamadı."; messageIsSuccess = false }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp),
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF0B0B16))
                        ) {
                            if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = Color(0xFF0B0B16))
                            else Text(if (isSignUp) "Ücretsiz hesap oluştur" else "Giriş yap", fontWeight = FontWeight.ExtraBold)
                        }

                        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(Color(0x26FFFFFF)))
                            Text("  veya  ", color = muted, fontSize = 11.sp)
                            Box(Modifier.weight(1f).height(1.dp).background(Color(0x26FFFFFF)))
                        }
                        Button(
                            onClick = {
                                if (googleClientId.isBlank()) {
                                    message = "Google girişi yapılandırılmadı. Google Web Client ID tanımlanmalı."
                                    messageIsSuccess = false
                                } else {
                                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail().requestIdToken(googleClientId).build()
                                    googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7F7FA), contentColor = Color(0xFF171526))
                        ) {
                            Text("G", color = Color(0xFF4285F4), fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("  Google ile devam et", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                isSignUp = !isSignUp
                                message = null
                                messageIsSuccess = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isSignUp) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol",
                                color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                if (email.isBlank()) {
                                    message = "Önce kayıt e-posta adresini yaz."
                                    messageIsSuccess = false
                                } else {
                                    scope.launch {
                                        busy = true
                                        val result = try { onResendConfirmation(email) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { com.hzyazilim.betalink.core.auth.AuthResult(message = "E-posta gönderilemedi. İnternet bağlantını kontrol et.") }
                                        finally { busy = false }
                                        message = result.message
                                        messageIsSuccess = result.message?.contains("gönderildi", true) == true
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Doğrulama e-postasını yeniden gönder", color = muted, fontSize = 11.sp) }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x252A2450)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x558B7CFF))
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀", fontSize = 18.sp)
                            Text("  Play Store test görevi", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("HEDEF", color = Color(0xFF86EFAC), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0x3322C55E)).padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            listOf("12" to "Tester hedefi", "14 gün" to "Test süresi").forEach { (value, label) ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                        .background(Color(0x33000000)).padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(label, color = muted, fontSize = 10.sp)
                                }
                            }
                        }
                        Text(
                            "Test gruplarını keşfet, katılımını takip et ve geliştiricilere dürüst geri bildirim ver.",
                            color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/privacy") }) {
                        Text("Gizlilik politikası", color = accent, fontSize = 10.sp)
                    }
                    Text("•", color = muted, fontSize = 10.sp)
                    TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/account-deletion") }) {
                        Text("Hesap ve veri silme", color = accent, fontSize = 10.sp)
                    }
                }
                Text(
                    "Devam ederek gerçek uygulama testlerine katılmayı ve dürüst geri bildirim vermeyi kabul edersin.",
                    color = Color(0xFF77778F), fontSize = 10.sp, modifier = Modifier.padding(bottom = 12.dp),
                    lineHeight = 15.sp
                )
            }
        }
    }
}
