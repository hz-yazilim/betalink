package com.hzyazilim.betalink.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.hzyazilim.betalink.BetalinkApp
import com.hzyazilim.betalink.core.auth.BetalinkUser
import com.hzyazilim.betalink.core.auth.SupabaseAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthGate() {
    val context = LocalContext.current
    val repository = remember { SupabaseAuthRepository(context) }
    var user by remember { mutableStateOf<BetalinkUser?>(null) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        user = withContext(Dispatchers.IO) { repository.restoreSession() }
        checking = false
    }
    if (checking) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Betalink hazırlanıyor…", modifier = Modifier.padding(top = 12.dp))
            }
        }
    } else if (user == null) {
        AuthScreen(
            onSignIn = { email, password -> withContext(Dispatchers.IO) { repository.signIn(email, password) } },
            onSignUp = { name, email, password -> withContext(Dispatchers.IO) { repository.signUp(email, password, name) } },
            onAuthenticated = { user = it }
        )
    } else {
        BetalinkApp(
            currentUser = user!!,
            onSignOut = {
                withContext(Dispatchers.IO) { repository.signOut() }
                user = null
            }
        )
    }
}

@Composable
private fun AuthScreen(
    onSignIn: suspend (String, String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onSignUp: suspend (String, String, String) -> com.hzyazilim.betalink.core.auth.AuthResult,
    onAuthenticated: (BetalinkUser) -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
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
                    if (email.isBlank() || password.length < 6 || (isSignUp && name.isBlank())) {
                        message = "E-posta, ad ve en az 6 karakterli şifre bilgilerini kontrol et."
                    } else {
                        scope.launch {
                            busy = true
                            message = null
                            val result = if (isSignUp) onSignUp(name, email, password) else onSignIn(email, password)
                            busy = false
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
            TextButton(onClick = { isSignUp = !isSignUp; message = null }) {
                Text(if (isSignUp) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol")
            }
            Text("Devam ederek gerçek uygulama testlerine katılmayı ve dürüst geri bildirim vermeyi kabul edersin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
