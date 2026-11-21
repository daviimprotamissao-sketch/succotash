package com.xtrasolucoes.xtraplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(vm: AppVM) {
    var createAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("xTra Player", style = MaterialTheme.typography.headlineLarge)
            Text(if (createAccount) "Crie sua conta" else "Entre na sua conta", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            vm.authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { if (createAccount) vm.register(email, password) else vm.signIn(email, password) },
                enabled = !vm.authBusy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (vm.authBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (createAccount) "Criar conta" else "Entrar")
            }
            TextButton(onClick = { createAccount = !createAccount }) {
                Text(if (createAccount) "Já tenho uma conta — entrar" else "Não tenho conta — criar")
            }
            Text("Suas playlists ficam vinculadas à sua conta e podem ser acessadas no site.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
