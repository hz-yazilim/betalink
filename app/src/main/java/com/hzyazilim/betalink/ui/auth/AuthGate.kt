package com.hzyazilim.betalink.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
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
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.completeEmailConfirmation(authCallbackUri) }
            }.getOrNull()
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
            onSignIn = { email, password -> withContext(Dispatchers.IO) { repository.signIn(email, password) } },
            onSignUp = { name, email, password -> withContext(Dispatchers.IO) { repository.signUp(email, password, name) } },
            onGoogleSignIn = { idToken -> withContext(Dispatchers.IO) { repository.signInWithGoogleIdToken(idToken) } },
            onResendConfirmation = { email -> withContext(Dispatchers.IO) { repository.resendSignupConfirmation(email) } },
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
    onSignIn: suspend (String, String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onSignUp: suspend (String, String, String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onGoogleSignIn: suspend (String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onResendConfirmation: suspend (String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    initialMessage: String?,
    onAuthenticated: (BetalinkUser) -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(initialMessage) }
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
                } else {
                    scope.launch {
                        busy = true
                        message = null
                        val authResult = try {
                            onGoogleSignIn(token)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            com.hzyazilim.betalink.core.auth.AuthResult(message = "Google girişi tamamlanamadı. İnternet bağlantını kontrol edip tekrar dene.")
                        } finally {
                            busy = false
                        }
                        if (authResult.user != null) onAuthenticated(authResult.user)
                        else message = authResult.message ?: "Google ile giriş tamamlanamadı."
                    }
                }
            } catch (error: ApiException) {
                message = when (error.statusCode) {
                    10 -> "Google girişi yapılandırma hatası (Kod 10: DEVELOPER_ERROR).\nKullanılan Web Client ID: $googleClientId\n\nKontrol adımları:\n1. Uygulamayı Android Studio'dan çalıştırıyorsanız DEBUG SHA-1 (63:8A:2F...) eklenmiş olmalı.\n2. SHA-1 anahtarının tam olarak bu Web Client ID'nin bulunduğu Google Cloud projesinde tanımlı olduğunu doğrulayın."
                    12500 -> "Google hesabıyla oturum açılamadı (Kod 12500). Lütfen cihazdaki Google Play Hizmetlerini ve aktif hesabı kontrol edin."
                    12501 -> "Google giriş penceresi kapatıldı veya iptal edildi."
                    else -> "Google girişi başarısız oldu (kod: ${error.statusCode})."
                }
            }
        } else {
            message = "Google girişi iptal edildi."
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("B", color = MaterialTheme.colorScheme.primary, fontSize = 46.sp, fontWeight = FontWeight.Black)
            Text("BETALINK", fontSize = 24.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text("Test et. Geri bildirim ver. Geliştir.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(28.dp))
            Text(if (isSignUp) "Hesabını oluştur" else "Tekrar hoş geldin", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            if (isSignUp) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Adın veya görünen adın") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
            }
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-posta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            Button(
                onClick = {
                    if (busy) return@Button
                    if (email.isBlank() || password.length < (if (isSignUp) 8 else 6) || (isSignUp && name.isBlank())) {
                        message = "Gmail adresini, adını ve şifreni kontrol et. Kayıtta şifre en az 8 karakter olmalı."
                    } else {
                        scope.launch {
                            busy = true
                            message = null
                            val result = try {
                                if (isSignUp) onSignUp(name, email, password) else onSignIn(email, password)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                com.hzyazilim.betalink.core.auth.AuthResult(message = "İşlem tamamlanamadı. İnternet bağlantını kontrol edip tekrar dene.")
                            } finally {
                                busy = false
                            }
                            if (result.user != null) onAuthenticated(result.user)
                            else message = result.message ?: "İşlem tamamlanamadı."
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(if (isSignUp) "Ücretsiz hesap oluştur" else "Giriş yap")
            }
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        message = "Önce kayıt e-posta adresini yaz."
                    } else {
                        scope.launch {
                            busy = true
                            val result = try {
                                onResendConfirmation(email)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                com.hzyazilim.betalink.core.auth.AuthResult(message = "E-posta gönderilemedi. İnternet bağlantını kontrol edip tekrar dene.")
                            } finally {
                                busy = false
                            }
                            message = result.message ?: "Doğrulama e-postası gönderilemedi."
                        }
                    }
                },
                enabled = !busy
            ) { Text("Doğrulama e-postasını yeniden gönder") }
            Button(
                onClick = {
                    if (googleClientId.isBlank()) {
                        message = "Google girişi henüz yapılandırılmadı. Google Web Client ID tanımlanmalı."
                    } else {
                        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestIdToken(googleClientId)
                            .build()
                        googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Google ile devam et") }
            TextButton(onClick = { isSignUp = !isSignUp; message = null }) {
                Text(if (isSignUp) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol")
            }
            TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/privacy") }) { Text("Gizlilik politikası") }
            TextButton(onClick = { uriHandler.openUri("https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/account-deletion") }) { Text("Hesap ve veri silme") }
            Text("Devam ederek gerçek uygulama testlerine katılmayı ve dürüst geri bildirim vermeyi kabul edersin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
