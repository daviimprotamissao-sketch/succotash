// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.xtrasolucoes.xtraplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.abs

/** [logo]/[num]: aparecem na barra do player de TV. [live]: esconde a barra de progresso. */
data class Play(val title: String, val url: String, val key: String? = null, val logo: String = "", val num: Int = 0, val live: Boolean = false)

/** Fade suave de entrada (usado ao abrir o player e as telas de detalhe). */
@Composable
fun Modifier.fadeInOnEnter(duration: Int = 260): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer { this.alpha = alpha.value }
}

@Composable
fun Root(vm: AppVM) {
    if (vm.profile == null) ProfilePicker(vm) else Shell(vm)
}

@Composable
fun Bg(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Papel de parede já vem desfocado e reduzido (1280x720 WebP, ~7 KB). Antes era um PNG 2560x1440
        // de 2 MB borrado em tempo real a cada quadro — pesado e causa de travadas/memória.
        Image(painterResource(R.drawable.wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        content()
    }
}

// ---------- Perfis ----------

@Composable
fun Avatar(p: Profile, size: Dp = 80.dp) {
    val colors = listOf(Color(0xFFE50914), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA))
    Box(
        Modifier.size(size).clip(CircleShape).background(colors[abs(p.id.hashCode()) % colors.size]),
        contentAlignment = Alignment.Center
    ) { Text(p.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White) }
}

@Composable
fun ProfilePicker(vm: AppVM) {
    var adding by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo), "xTra Player", Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(12.dp))
        Text("Quem está assistindo?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(vm.profiles) { p ->
                Column(Modifier.clickable { vm.selectProfile(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(p)
                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.kids) Text("Infantil", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(Modifier.clickable { adding = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Novo perfil")
                }
            }
        }
    }
    if (adding) ProfileDialog({ adding = false }) { n, k -> vm.addProfile(n, k); adding = false }
}

@Composable
fun ProfileDialog(onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo perfil") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = kids, onCheckedChange = { kids = it })
                    Text("Perfil infantil (sem conteúdo +18)")
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onOk(name.trim(), kids) }) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------- PIN ----------

@Composable
fun PinDialog(title: String, error: String? = null, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(pin) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun GuardDialog(vm: AppVM, action: () -> Unit, onClose: () -> Unit) {
    var err by remember { mutableStateOf<String?>(null) }
    PinDialog("Digite o PIN", err, onClose) {
        if (vm.checkPin(it)) { vm.unlocked = true; onClose(); action() } else err = "PIN incorreto"
    }
}

// ---------- Estrutura principal ----------

@Composable
fun Shell(vm: AppVM) {
    var screen by remember { mutableStateOf(0) } // 0 início, 1 TV, 2 filmes, 3 séries, 4 playlists, 5 perfis, 6 ajustes
    var play by remember { mutableStateOf<Play?>(null) }
    var openSerie by remember { mutableStateOf<Serie?>(null) }
    var openVod by remember { mutableStateOf<Vod?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var urlDialog by remember { mutableStateOf(false) }

    val gate: (Boolean, () -> Unit) -> Unit = { adult, a -> if (vm.locked(adult)) pending = a else a() }
    val onPlay: (Boolean, Play) -> Unit = { adult, p -> gate(adult) { p.key?.let { vm.touch(it) }; play = p } }

    pending?.let { a -> GuardDialog(vm, a) { pending = null } }
    if (urlDialog) UrlDialog({ urlDialog = false }) { play = Play("Vídeo", it); urlDialog = false }

    val p = play
    if (p != null) {
        BackHandler { play = null }
        PlayerScreen(p, vm.aspect, { vm.setAspect(it) }) { play = null }
        return
    }
    val v = openVod
    if (v != null) {
        BackHandler { openVod = null }
        Bg { VodDetail(vm, v, onPlay) { openVod = null } }
        return
    }
    val s = openSerie
    if (s != null) {
        BackHandler { openSerie = null }
        Bg { SeriesDetail(vm, s, onPlay) { openSerie = null } }
        return
    }

    BackHandler(enabled = screen != 0) { screen = 0 }
    Bg {
        Column(Modifier.fillMaxSize()) {
            Status(vm)
            Box(Modifier.weight(1f)) {
                // Troca de aba com transição suave (fade cruzado) em vez do corte abrupto.
                AnimatedContent(
                    targetState = screen,
                    label = "screen",
                    transitionSpec = {
                        fadeIn(tween(260)) togetherWith fadeOut(tween(160))
                    }
                ) { scr ->
                    when (scr) {
                        0 -> Home(vm, { screen = it }, { openVod = it }, { openSerie = it }) { urlDialog = true }
                        1 -> LiveScreen(vm, onPlay)
                        2 -> Catalog(vm, vm.vodUi, "Filmes", vm.vodItems, "Nenhum filme. Adicione uma playlist Xtream Codes com filmes na aba Playlists.") { c ->
                            gate(c.adult) { openVod = c.ref as Vod }
                        }
                        3 -> Catalog(vm, vm.serUi, "Séries", vm.serItems, "Nenhuma série. Adicione uma playlist Xtream Codes com séries na aba Playlists.") { c ->
                            gate(c.adult) { openSerie = c.ref as Serie }
                        }
                        4 -> PlaylistsScreen(vm) { screen = 0 }
                        5 -> ProfilesScreen(vm) { screen = 0 }
                        else -> SettingsScreen(vm) { screen = 0 }
                    }
                }
            }
        }
    }
}

@Composable
fun Status(vm: AppVM) {
    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color.White, trackColor = Color.Transparent)
    vm.error?.let {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { vm.load() }) { Text("Tentar de novo") }
        }
    }
}

@Composable
fun UrlDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reproduzir vídeo por URL") },
        text = { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://.../video.m3u8") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onOk(url.trim()) }) { Text("Reproduzir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------- Ajustes ----------

@Composable
fun SettingsScreen(vm: AppVM, onBack: () -> Unit) {
    var guard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var newPin by remember { mutableStateOf(false) }
    var pinErr by remember { mutableStateOf<String?>(null) }
    val needPin: (() -> Unit) -> Unit = { a -> if (vm.hasPin) guard = a else a() }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp).background(Glass, RoundedCornerShape(12.dp)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = onBack) { Text("← Voltar") } }
        item { Text("Ajustes", style = MaterialTheme.typography.headlineSmall) }

        item { Spacer(Modifier.height(4.dp)); Text("Controle parental", style = MaterialTheme.typography.titleMedium) }
        item {
            Text("Perfis infantis nunca mostram canais, filmes ou séries +18. Nos demais perfis, o conteúdo +18 pede o PIN.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exigir PIN para conteúdo +18", Modifier.weight(1f))
                Switch(checked = vm.parental, onCheckedChange = { v -> if (v) vm.setParental(true) else needPin { vm.setParental(false) } })
            }
        }
        item {
            Button(onClick = { needPin { newPin = true } }) { Text(if (vm.hasPin) "Alterar PIN" else "Definir PIN") }
        }

        item { Spacer(Modifier.height(8.dp)); Text("Formato de tela padrão", style = MaterialTheme.typography.titleMedium) }
        item { Text("Usado assim que o player abre (pode trocar na hora pelo botão ⛶).", style = MaterialTheme.typography.bodySmall) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ASPECTS.forEachIndexed { i, n ->
                    Row(Modifier.fillMaxWidth().clickable { vm.setAspect(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.aspect == i, onClick = { vm.setAspect(i) })
                        Text(n)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Sobre", style = MaterialTheme.typography.titleMedium)
                Text("$APP_NAME 2.2", style = MaterialTheme.typography.bodySmall)
                Text("Autoria: $AUTHOR", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    guard?.let { a -> GuardDialog(vm, a) { guard = null } }
    if (newPin) PinDialog("Novo PIN (4 a 6 dígitos)", pinErr, { newPin = false; pinErr = null }) {
        if (it.length in 4..6) { vm.setPin(it); newPin = false; pinErr = null } else pinErr = "Use de 4 a 6 dígitos"
    }
}

// ---------- Perfis (aba própria) ----------

@Composable
fun ProfilesScreen(vm: AppVM, onBack: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var guard by remember { mutableStateOf<(() -> Unit)?>(null) }
    val needPin: (() -> Unit) -> Unit = { a -> if (vm.hasPin) guard = a else a() }

    Column(Modifier.fillMaxSize().padding(12.dp).background(Glass, RoundedCornerShape(12.dp)).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Voltar") }
        Text("Perfis", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("Toque num perfil para usá-lo agora.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(vm.profiles) { p ->
                val active = vm.profile?.id == p.id
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).then(if (active) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable { vm.selectProfile(p) }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(p)
                    Spacer(Modifier.height(6.dp))
                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (active) Text("Em uso", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE50914))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Infantil", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = p.kids, onCheckedChange = { v -> if (v) vm.setKids(p, true) else needPin { vm.setKids(p, false) } })
                    }
                    if (vm.profiles.size > 1) TextButton(onClick = { needPin { vm.deleteProfile(p) } }) { Text("Excluir") }
                }
            }
            item {
                Column(Modifier.clickable { adding = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Novo perfil")
                }
            }
        }
    }
    if (adding) ProfileDialog({ adding = false }) { n, k -> vm.addProfile(n, k); adding = false }
    guard?.let { a -> GuardDialog(vm, a) { guard = null } }
}

// ---------- Playlists (aba própria, com o MAC do aparelho) ----------

@Composable
fun PlaylistsScreen(vm: AppVM, onBack: () -> Unit) {
    var add by remember { mutableStateOf(false) }
    var guard by remember { mutableStateOf<(() -> Unit)?>(null) }
    val needPin: (() -> Unit) -> Unit = { a -> if (vm.hasPin) guard = a else a() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { vm.loadCloud() }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp).background(Glass, RoundedCornerShape(12.dp)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = onBack) { Text("← Voltar") } }
        item { Text("Playlists", style = MaterialTheme.typography.headlineSmall) }

        item {
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Color(0x40FFFFFF)).padding(14.dp)) {
                Text("Endereço MAC deste aparelho", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.deviceMac, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(vm.deviceMac)) }) { Text("Copiar") }
                }
                Text("Use este endereço no site para adicionar ou remover listas remotamente. Toque em \"Sincronizar agora\" para trazer o que foi feito lá para o aparelho.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.loadCloud() }, enabled = !vm.cloudLoading) { Text(if (vm.cloudLoading) "Sincronizando…" else "Sincronizar agora") }
                Spacer(Modifier.width(10.dp))
                vm.cloudError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        item { Spacer(Modifier.height(4.dp)); Text("Suas listas", style = MaterialTheme.typography.titleMedium) }
        items(vm.providers) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.provider.id == p.id, onClick = { vm.selectProvider(p) })
                Column(Modifier.weight(1f)) {
                    Text(p.name)
                    Text(when (p.type) { "xtream" -> "Xtream Codes"; "m3u" -> "Lista M3U"; else -> "Incluída no app" }, style = MaterialTheme.typography.bodySmall)
                }
                if (p.id != "builtin") TextButton(onClick = {
                    needPin {
                        val cp = vm.cloudPlaylists.firstOrNull { "cloud_" + it.key == p.id }
                        if (cp != null) vm.removeCloudPlaylist(cp) else vm.deleteProvider(p)
                    }
                }) { Text("Remover") }
            }
        }
        item { Button(onClick = { add = true }) { Text("+ Adicionar lista") } }
    }

    if (add) ProviderDialog({ add = false }) { p -> vm.addCloudPlaylist(p.name, p.type, p.server, p.user, p.pass); add = false }
    guard?.let { a -> GuardDialog(vm, a) { guard = null } }
}

@Composable
fun ProviderDialog(onDismiss: () -> Unit, onOk: (Provider) -> Unit) {
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar provedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = xtream, onClick = { xtream = true }, label = { Text("Xtream Codes") })
                    FilterChip(selected = !xtream, onClick = { xtream = false }, label = { Text("Lista M3U") })
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do provedor") }, singleLine = true)
                OutlinedTextField(value = server, onValueChange = { server = it },
                    label = { Text(if (xtream) "Servidor (http://host:porta)" else "URL da lista M3U") }, singleLine = true)
                if (xtream) {
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuário") }, singleLine = true)
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Senha") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = server.trim()
                val url = if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
                if (name.isBlank() || s.isBlank() || (xtream && (user.isBlank() || pass.isBlank()))) err = "Preencha todos os campos"
                else onOk(Provider("prov" + System.currentTimeMillis(), name.trim(), if (xtream) "xtream" else "m3u", url, user.trim(), pass))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------- Player ----------

val ASPECTS = listOf("Ajustar", "Esticar (tela toda)", "4:3 (800×600)", "Preencher", "Preencher 4K")

/** Um único ExoPlayer por tela (antes: um player novo a cada canal tocado) + estado de buffering/erro. */
@Stable
class PlayerState(val player: ExoPlayer) {
    var buffering by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var url: String = ""
    private var triedHttp = false

    fun start(u: String) {
        url = u
        triedHttp = false
        error = null
        if (u.isBlank()) {
            player.stop(); player.clearMediaItems(); buffering = false
            return
        }
        buffering = true
        player.setMediaItem(MediaItem.fromUri(u))
        player.prepare()
        player.playWhenReady = true
    }

    fun onError() {
        if (!triedHttp && url.startsWith("https://")) {
            // Muitos servidores de IPTV só funcionam em http; tenta de novo sem TLS.
            triedHttp = true
            buffering = true
            player.setMediaItem(MediaItem.fromUri(url.replaceFirst("https://", "http://")))
            player.prepare()
            player.playWhenReady = true
        } else {
            buffering = false
            error = "Não foi possível reproduzir."
        }
    }
}

@Composable
fun rememberPlayer(url: String, preview: Boolean = false, debounceMs: Long = 0L): PlayerState {
    val ctx = LocalContext.current
    val st = remember {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android) xTraPlayer")
        // Buffers pequenos = o canal abre mais rápido (principalmente na prévia).
        val lc = (if (preview) DefaultLoadControl.Builder().setBufferDurationsMs(2_500, 10_000, 600, 1_500)
                  else DefaultLoadControl.Builder().setBufferDurationsMs(12_000, 50_000, 1_000, 2_500))
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .setLoadControl(lc)
            .build()
        // A prévia pequena não precisa de 1080p: limita a 480p e economiza rede/CPU.
        if (preview) p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(854, 480).build()
        PlayerState(p)
    }
    DisposableEffect(st) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                st.buffering = st.error == null && st.url.isNotBlank() &&
                    (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE)
            }
            override fun onPlayerError(error: PlaybackException) { st.onError() }
        }
        st.player.addListener(listener)
        // Para o som/vídeo quando o app vai para segundo plano.
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) st.player.pause()
            else if (ev == Lifecycle.Event.ON_START && st.url.isNotBlank() && st.error == null) st.player.play()
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose {
            owner?.lifecycle?.removeObserver(obs)
            st.player.removeListener(listener)
            st.player.release()
        }
    }
    LaunchedEffect(url) {
        if (debounceMs > 0 && url.isNotBlank()) delay(debounceMs)
        st.start(url)
    }
    return st
}

@Composable
fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier, mode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = mode
                this.player = player
            }
        },
        update = { it.resizeMode = mode },
        onRelease = { it.player = null }
    )
}

@Composable
fun GlassBtn(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(48.dp).tvFocus(CircleShape).clip(CircleShape).background(Color(0x47FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Ícone de "expandir/recolher tela" desenhado em vetor (sem depender de pacote de ícones). */
@Composable
fun ExpandGlyph(expanded: Boolean, sz: Dp = 20.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(sz)) {
        val s = this.size.minDimension
        val k = s * 0.30f
        val w = s * 0.11f
        val a = if (expanded) s * 0.40f else w / 2f
        val dir = if (expanded) -1f else 1f
        val path = androidx.compose.ui.graphics.Path()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            path.moveTo(x + dx * k, y); path.lineTo(x, y); path.lineTo(x, y + dy * k)
        }
        corner(a, a, dir, dir)
        corner(s - a, a, -dir, dir)
        corner(a, s - a, dir, -dir)
        corner(s - a, s - a, -dir, -dir)
        drawPath(
            path, Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

private fun fmtTime(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Barra de progresso só para filmes/episódios (sem botão de pausar nem pular). Em canal ao vivo some. */
@Composable
private fun SeekBar(player: ExoPlayer, modifier: Modifier) {
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var isLive by remember { mutableStateOf(true) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            isLive = player.isCurrentMediaItemLive
            delay(500)
        }
    }
    if (isLive || dur <= 0L) {
        Spacer(modifier)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(fmtTime(pos), color = Color.White, fontSize = 12.sp)
        Slider(
            value = (drag ?: (pos.toFloat() / dur)).coerceIn(0f, 1f),
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { player.seekTo((it * dur).toLong()) }; drag = null },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x44FFFFFF))
        )
        Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
    }
}

/** Barra inferior: logo do canal, número, ajustes e expandir tela. */
@Composable
private fun PlayerBar(item: Play, st: PlayerState, expanded: Boolean, onSettings: () -> Unit, onExpand: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (item.logo.isNotBlank()) {
            AsyncImage(
                model = item.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x38FFFFFF)).padding(6.dp)
            )
        }
        if (item.num > 0) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x38FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("${item.num}", color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
        if (!item.live) SeekBar(st.player, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        GlassBtn(onSettings) { Icon(painterResource(R.drawable.ic_settings), null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        GlassBtn(onExpand) { ExpandGlyph(expanded) }
    }
}

@Composable
fun PlayerScreen(item: Play, aspect: Int, onAspect: (Int) -> Unit, onClose: () -> Unit) {
    val st = rememberPlayer(item.url)
    var bar by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }

    // A barra aparece ao tocar na tela ou apertar qualquer botão, e some sozinha depois de 4 s.
    LaunchedEffect(bar, tick, menu) {
        if (bar && !menu) { delay(4000); bar = false }
    }
    LaunchedEffect(bar) {
        if (!bar) { try { focus.requestFocus() } catch (e: Exception) { } }
    }
    BackHandler(enabled = menu) { menu = false }

    // "Preencher 4K": corta as bordas e pede sempre a melhor qualidade (até 3840x2160).
    LaunchedEffect(st, aspect) {
        val b = st.player.trackSelectionParameters.buildUpon()
        st.player.trackSelectionParameters =
            if (aspect == 4) b.setMaxVideoSize(3840, 2160).setForceHighestSupportedBitrate(true).build()
            else b.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).setForceHighestSupportedBitrate(false).build()
    }

    val mode = when (aspect) {
        1, 2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        3, 4 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).fadeInOnEnter()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key != Key.DirectionCenter && e.key != Key.Enter) {
                    if (!bar) bar = true
                    tick++
                }
                false
            }
    ) {
        val frame = if (aspect == 2) Modifier.align(Alignment.Center).aspectRatio(4f / 3f) else Modifier.fillMaxSize()
        PlayerSurface(st.player, frame, mode)

        // Camada de toque por cima do vídeo: toque (ou OK do controle) mostra/esconde a barra.
        Box(
            Modifier.fillMaxSize().focusRequester(focus).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {
                if (menu) menu = false else if (bar) bar = false else { bar = true; tick++ }
            }
        )

        if (st.buffering && st.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(42.dp), color = Color.White, strokeWidth = 3.dp)
        }
        st.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, color = Color.White)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { st.start(item.url) }) { Text("Tentar de novo", color = Color.White) }
            }
        }

        AnimatedVisibility(
            visible = bar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 }
        ) {
            PlayerBar(
                item, st, expanded = aspect != 0,
                onSettings = { menu = !menu; tick++ },
                onExpand = { onAspect(if (aspect == 0) 3 else 0); tick++ }
            )
        }

        // Painel de ajustes (formato da tela), logo acima da barra.
        AnimatedVisibility(
            visible = menu,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 92.dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut(tween(120))
        ) {
            val r = RoundedCornerShape(20.dp)
            Column(Modifier.width(230.dp).background(Color(0xEB101012), r).border(1.dp, Color(0x33FFFFFF), r).padding(8.dp)) {
                Text("Formato da tela", color = Color(0x99FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp))
                val rr = RoundedCornerShape(14.dp)
                ASPECTS.forEachIndexed { i, n ->
                    val on = i == aspect
                    Row(
                        Modifier.fillMaxWidth().tvFocus(rr).clip(rr).background(if (on) Color.White else Color.Transparent)
                            .clickable { onAspect(i); menu = false }.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) { Text(n, color = if (on) Color.Black else Color.White, fontSize = 14.sp) }
                }
            }
        }
    }
}
