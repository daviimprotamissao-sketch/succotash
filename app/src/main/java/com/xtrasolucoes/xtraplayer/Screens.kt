// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.xtrasolucoes.xtraplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Glass = Color(0x99000000)
private val PT = Locale("pt", "BR")

/** Vidro translúcido leve: preenchimento + borda fina (sem clip/gradiente por item, que pesavam nas listas). */
fun Modifier.liquid(shape: Shape = RoundedCornerShape(16.dp), solid: Color? = null): Modifier =
    if (solid != null) this.background(solid, shape)
    else this.background(Color(0x59000000), shape).border(1.dp, Color(0x33FFFFFF), shape)

/**
 * Realce no item focado (controle remoto / D-pad). A animação só é lida na fase de desenho,
 * então focar/desfocar não recompõe a tela. Deve vir ANTES do fundo/clickable para escalar tudo junto.
 */
@Composable
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(10.dp), scaleTo: Float = 1.035f): Modifier {
    var f by remember { mutableStateOf(false) }
    val anim = animateFloatAsState(if (f) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "tvFocus")
    return this
        .onFocusChanged { f = it.isFocused }
        .graphicsLayer { val s = 1f + (scaleTo - 1f) * anim.value; scaleX = s; scaleY = s }
        .drawWithContent {
            drawContent()
            val a = anim.value
            if (a > 0.01f) {
                drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = a), style = Stroke(3.dp.toPx()))
            }
        }
}

// ---------- Degradê preto e branco em movimento ----------

private val BW = listOf(Color(0xFF000000), Color(0xFF2B2B2B), Color(0xFFF2F2F2), Color(0xFF2B2B2B), Color(0xFF000000))
private val TextShadow = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1.5f), 8f))

/** UMA animação compartilhada por todas as linhas (em vez de uma por item). */
@Composable
fun rememberBwShift(): State<Float> {
    val t = rememberInfiniteTransition(label = "bw")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart), label = "bwShift")
}

/** Fundo degradê preto↔branco que desliza sem parar (loop sem emenda). Só redesenha, nunca recompõe. */
fun Modifier.movingBW(shift: State<Float>, shape: Shape): Modifier = this.clip(shape).drawBehind {
    val len = size.width * 1.5f + 1f
    val x = shift.value * len
    drawRect(Brush.linearGradient(BW, start = Offset(x, 0f), end = Offset(x + len, 0f), tileMode = TileMode.Repeated))
}

@Composable
fun Clock(compact: Boolean = false) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Column(horizontalAlignment = Alignment.End) {
        if (!compact) Text(SimpleDateFormat("EEE dd MMM", PT).format(now), style = MaterialTheme.typography.bodySmall, color = Color.White)
        Text(SimpleDateFormat(if (compact) "HH:mm" else "hh:mm a", PT).format(now), fontSize = if (compact) 14.sp else 26.sp,
            fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun PosterCard(title: String, img: String, modifier: Modifier = Modifier.width(120.dp), onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.tvFocus(shape).clickable(onClick = onClick)) {
        AsyncImage(
            model = img.ifBlank { null }, contentDescription = title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Color(0xFF2A2A2A)).border(1.dp, Color(0x66FFFFFF), shape)
        )
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            color = Color.White, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------- Início ----------

@Composable
fun IconBtn(icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).tvFocus(CircleShape).liquid(CircleShape).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@Composable
fun Tile(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.tvFocus(shape).liquid(shape).clip(shape).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun Home(vm: AppVM, go: (Int) -> Unit, onVod: (Vod) -> Unit, onSerie: (Serie) -> Unit, onUrl: () -> Unit) {
    val movies = remember(vm.vodItems) { vm.vodItems.asSequence().filter { !it.adult }.take(12).toList() }
    val shows = remember(vm.serItems) { vm.serItems.asSequence().filter { !it.adult }.take(12).toList() }
    val chans = remember(vm.liveData) { vm.liveData.chans.asSequence().filter { !it.adult }.take(15).toList() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBtn(R.drawable.ic_settings) { go(6) }
                IconBtn(R.drawable.ic_profile) { go(5) }
                IconBtn(R.drawable.ic_refresh) { vm.load() }
            }
            Image(painterResource(R.drawable.logo), "xTra Player", Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Clock() }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("TV ao vivo", R.drawable.ic_tv, Modifier.size(170.dp, 64.dp)) { go(1) }
                Tile("Filmes", R.drawable.ic_movies, Modifier.size(170.dp, 64.dp)) { go(2) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Séries", R.drawable.ic_series, Modifier.size(170.dp, 64.dp)) { go(3) }
                Tile("Playlists", R.drawable.ic_playlist, Modifier.size(170.dp, 64.dp)) { go(4) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Perfis", R.drawable.ic_profile, Modifier.size(170.dp, 64.dp)) { go(5) }
                Tile("Vídeo por URL", R.drawable.ic_play, Modifier.size(170.dp, 64.dp)) { onUrl() }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Em destaque", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(6.dp))
        if (movies.isEmpty() && shows.isEmpty() && chans.isEmpty()) {
            Text(if (vm.loading) "Carregando…" else "Nada para mostrar ainda. Adicione uma lista na aba Playlists.", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(movies, key = { it.key }) { m -> PosterCard(m.title, m.img, Modifier.width(96.dp)) { onVod(m.ref as Vod) } }
            items(shows, key = { it.key }) { x -> PosterCard(x.title, x.img, Modifier.width(96.dp)) { onSerie(x.ref as Serie) } }
            if (movies.isEmpty() && shows.isEmpty()) items(chans, key = { it.id }) { c ->
                Column(Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = c.logo.ifBlank { null }, contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)))
                    Text(c.display, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Versão 2.2", Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
            Text("xTra Soluções", Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
            Text("${vm.provider.name} • ${vm.profile?.name ?: ""}", color = Color.White, fontSize = 12.sp)
        }
    }
}

// ---------- TV ao vivo ----------

@Composable
fun SearchGlyph(sz: Dp = 18.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(sz)) {
        val s = this.size.minDimension
        drawCircle(Color(0xCCFFFFFF), radius = s * 0.32f, center = Offset(s * 0.42f, s * 0.42f), style = Stroke(width = s * 0.11f))
        drawLine(Color(0xCCFFFFFF), Offset(s * 0.66f, s * 0.66f), Offset(s * 0.92f, s * 0.92f), strokeWidth = s * 0.12f, cap = StrokeCap.Round)
    }
}

/** Busca em formato de pílula, com foco animado e botão de limpar. */
@Composable
fun SearchField(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Color.White else Color(0x40FFFFFF), tween(180), label = "searchBorder")
    Row(
        modifier.height(46.dp).background(Color(0x66000000), shape).border(1.dp, border, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchGlyph()
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = Color(0x99FFFFFF), fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
        AnimatedVisibility(visible = value.isNotEmpty(), enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0x40FFFFFF)).clickable { onValue("") }, contentAlignment = Alignment.Center) {
                Text("✕", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun KeyChip(color: Color, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.tvFocus(shape).liquid(shape).clip(shape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupRow(name: String, count: Int, shift: State<Float>, onClick: () -> Unit) {
    val r = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).tvFocus(r).movingBW(shift, r)
            .border(1.dp, Color(0x40FFFFFF), r).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextShadow)
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x66000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
            Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = Color.White, fontSize = 18.sp, style = TextShadow)
    }
}

@Composable
private fun ChannelRow(num: Int, ch: Live, on: Boolean, fav: Boolean, onClick: () -> Unit) {
    val r = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (on) Color.White else Color(0x4D000000), tween(220), label = "chBg")
    val fg by animateColorAsState(if (on) Color.Black else Color.White, tween(220), label = "chFg")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).tvFocus(r).clip(r).background(bg)
            .border(1.dp, if (on) Color.Transparent else Color(0x26FFFFFF), r)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$num", Modifier.width(30.dp), color = fg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        AsyncImage(
            model = ch.logo.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x33FFFFFF)).padding(2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(ch.display, Modifier.weight(1f), color = fg, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (fav) Text("★", color = fg, fontSize = 14.sp)
    }
}

@Composable
fun SkeletonRows(n: Int = 8) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "skeletonAlpha")
    Column {
        repeat(n) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).height(50.dp)
                    .graphicsLayer { alpha = a }.background(Color(0x40FFFFFF), RoundedCornerShape(18.dp))
            )
        }
    }
}

@Composable
private fun LiveBadge() {
    val t = rememberInfiniteTransition(label = "liveDot")
    val a by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "liveDotAlpha")
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Color(0xFFFF3B30)))
        Spacer(Modifier.width(5.dp))
        Text("AO VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PreviewCard(sel: Live?, ps: PlayerState, onOpen: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.clip(shape).background(Color(0xFF0B0B0D)).border(1.dp, Color(0x40FFFFFF), shape).clickable(onClick = onOpen)) {
        if (sel == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_tv), null, tint = Color(0x66FFFFFF), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Text("Selecione um canal", color = Color(0x99FFFFFF), fontSize = 13.sp)
            }
        } else {
            PlayerSurface(ps.player, Modifier.fillMaxSize())
            if (ps.buffering && ps.error == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(30.dp), color = Color.White, strokeWidth = 2.5.dp)
            }
            if (ps.error != null) Text("Sem sinal neste canal", Modifier.align(Alignment.Center), color = Color.White, fontSize = 13.sp)
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                ExpandGlyph(false, 15.dp)
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 10.dp)
            ) {
                Crossfade(sel, label = "previewInfo") { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = c.logo.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)).padding(2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(c.display, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LiveBadge()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgRow(pr: Prog, nowMs: Long, fmt: SimpleDateFormat) {
    val r = RoundedCornerShape(16.dp)
    val live = nowMs in pr.start * 1000 until pr.stop * 1000
    val frac = if (live && pr.stop > pr.start) ((nowMs / 1000f - pr.start) / (pr.stop - pr.start)).coerceIn(0f, 1f) else 0f
    Row(
        Modifier.fillMaxWidth().clip(r).background(if (live) Color(0x40FFFFFF) else Color(0x33000000))
            .drawBehind { if (frac > 0f) drawRect(Color(0x30FFFFFF), size = Size(size.width * frac, size.height)) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fmt.format(Date(pr.start * 1000)), color = Color.White.copy(alpha = if (live) 1f else 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(pr.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
            fontWeight = if (live) FontWeight.Bold else FontWeight.Normal)
        if (live) Text("AGORA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DayChip(num: Int, wd: String, on: Boolean, onClick: () -> Unit) {
    val r = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (on) Color.White else Color(0x59000000), tween(200), label = "dayBg")
    val fg by animateColorAsState(if (on) Color.Black else Color.White, tween(200), label = "dayFg")
    Column(
        Modifier.fillMaxWidth().tvFocus(r).clip(r).background(bg).border(1.dp, if (on) Color.Transparent else Color(0x33FFFFFF), r)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$num", color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(wd, color = fg, fontSize = 11.sp)
    }
}

@Composable
fun LiveScreen(vm: AppVM, onPlay: (Boolean, Play) -> Unit) {
    var q by remember { mutableStateOf("") }
    var sortAz by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var progs by remember { mutableStateOf<List<Prog>?>(null) }
    val shift = rememberBwShift()

    val data = vm.liveData
    val favs = vm.favs
    val favList = remember(data, favs) { if (favs.isEmpty()) emptyList() else data.chans.filter { it.id in favs } }
    val sections = remember(data, favList) { if (favList.isEmpty()) data.groups else listOf(Group("★ Favoritos", favList)) + data.groups }

    // Busca com "debounce": só filtra 160 ms depois de parar de digitar, e fora da thread principal.
    var qDebounced by remember { mutableStateOf("") }
    LaunchedEffect(q) {
        if (q.isBlank()) qDebounced = "" else { delay(160); qDebounced = q }
    }
    val needle = remember(qDebounced) { Brand.fold(qDebounced.trim()) }
    val group = vm.liveGroup
    val sel = vm.liveSel
    val current by produceState(emptyList<Live>(), data, sections, group, needle, sortAz) {
        value = withContext(Dispatchers.Default) {
            val base = if (needle.isNotEmpty()) data.chans.filter { it.search.contains(needle) }
            else sections.firstOrNull { it.name == group }?.list.orEmpty()
            if (sortAz) base.sortedBy { it.display.lowercase() } else base
        }
    }
    val selNum = remember(current, sel) { if (sel == null) 0 else current.indexOfFirst { it.id == sel.id } + 1 }
    fun playOf(c: Live, n: Int) = Play(c.display, c.url, null, c.logo, n, true)

    LaunchedEffect(sel?.id, vm.provider.id) {
        progs = null
        val s = sel
        progs = if (s != null && vm.provider.type == "xtream")
            withContext(Dispatchers.IO) { try { Xtream(vm.provider).epg(s.id) } catch (e: Exception) { emptyList() } }
        else emptyList()
    }
    // Um único player para a prévia, com 250 ms de espera: percorrer canais rápido não abre conexões à toa.
    val ps = rememberPlayer(sel?.url.orEmpty(), preview = true, debounceMs = 250L)

    val showList = q.isNotBlank() || group != null
    val title = if (q.isNotBlank()) "Busca" else (group ?: "Categorias")
    val back = { if (q.isNotBlank()) q = "" else vm.liveGroup = null }
    BackHandler(enabled = showList) { back() }
    val r16 = RoundedCornerShape(18.dp)

    Column(Modifier.fillMaxSize()) {
        // Barra superior
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).liquid(r16).clip(r16).then(if (showList) Modifier.clickable { back() } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showList) Text("‹  ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x40FFFFFF)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                    Text("${if (showList) current.size else sections.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1.9f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyChip(Color(0xFFFF453A), if (sortAz) "A-Z" else "Ordenar", Modifier.weight(1f)) { sortAz = !sortAz }
                KeyChip(Color(0xFF30D158), "Categoria", Modifier.weight(1f)) { vm.liveGroup = null; q = "" }
                KeyChip(Color(0xFFFFD60A), "Favoritos", Modifier.weight(1f)) {
                    if (favList.isNotEmpty()) { vm.liveGroup = "★ Favoritos"; q = "" }
                }
                Box(Modifier.weight(1f)) {
                    KeyChip(Color(0xFF3D7BFF), "Menu", Modifier.fillMaxWidth()) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Tela cheia") }, onClick = {
                            menu = false; sel?.let { onPlay(it.adult, playOf(it, selNum)) }
                        })
                        DropdownMenuItem(text = { Text(if (sel != null && sel.id in favs) "Remover dos favoritos" else "Favoritar canal") }, onClick = {
                            menu = false; sel?.let { vm.toggleFav(it.id) }
                        })
                        DropdownMenuItem(text = { Text("Recarregar lista") }, onClick = { menu = false; vm.load() })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.liquid(r16).padding(horizontal = 12.dp, vertical = 11.dp)) { Clock(compact = true) }
        }

        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Lista (categorias ou canais)
            Column(Modifier.weight(0.95f)) {
                SearchField(q, { q = it }, "Buscar canal", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AnimatedContent(
                    targetState = showList, label = "liveList",
                    transitionSpec = {
                        if (targetState)
                            (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(160)))
                        else
                            (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(160)))
                    }
                ) { listMode ->
                    if (!listMode) {
                        if (sections.isEmpty()) {
                            if (vm.loading) SkeletonRows()
                            else Text("Nenhum canal. Toque em Menu › Recarregar lista.", color = Color(0xB3FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                            items(sections, key = { it.name }) { g -> GroupRow(g.name, g.list.size, shift) { vm.liveGroup = g.name } }
                        }
                    } else {
                        if (current.isEmpty()) {
                            Text(if (needle.isNotEmpty()) "Nenhum canal encontrado." else "Grupo vazio.", color = Color(0xB3FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                            itemsIndexed(current, key = { _, c -> c.id }) { i, ch ->
                                val on = ch.id == sel?.id
                                ChannelRow(i + 1, ch, on, ch.id in favs) {
                                    if (on) onPlay(ch.adult, playOf(ch, i + 1)) else vm.liveSel = ch
                                }
                            }
                        }
                    }
                }
            }

            // Pré-visualização + guia
            Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewCard(sel, ps, { sel?.let { onPlay(it.adult, playOf(it, selNum)) } }, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                val list = progs
                val fmt = remember { SimpleDateFormat("HH:mm", PT) }
                val dayStart = remember(day) {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis + day * 86_400_000L
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        sel == null -> {}
                        list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color.White, trackColor = Color(0x33FFFFFF))
                        vm.provider.type != "xtream" -> Text("Guia de programação indisponível neste provedor.", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                        else -> {
                            val today = remember(list, dayStart) { list.filter { it.start * 1000 in dayStart until dayStart + 86_400_000L } }
                            if (today.isEmpty()) Text("Sem programação neste dia.", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                            else {
                                val nowMs = remember(today) { System.currentTimeMillis() }
                                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                                    items(today) { pr -> ProgRow(pr, nowMs, fmt) }
                                }
                            }
                        }
                    }
                }
            }

            // Dias
            val days = remember {
                (0..6).map { i ->
                    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
                    c.get(Calendar.DAY_OF_MONTH) to SimpleDateFormat("EEE", PT).format(c.time).replaceFirstChar { it.uppercase() }
                }
            }
            Column(Modifier.width(60.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, (n, wd) -> DayChip(n, wd, day == i) { day = i } }
            }
        }
    }
}

// ---------- Filmes e séries ----------

@Composable
fun SideRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val r = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (selected) Color.White else Color(0x4D000000), tween(200), label = "sideBg")
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(200), label = "sideFg")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).tvFocus(r).clip(r).background(bg)
            .border(1.dp, if (selected) Color.Transparent else Color(0x26FFFFFF), r)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(name, Modifier.weight(1f), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("$count", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

class CatView(val shown: List<CatItem>, val recCount: Int, val favCount: Int)

@Composable
fun Catalog(vm: AppVM, st: CatState, title: String, items: List<CatItem>, empty: String, onOpen: (CatItem) -> Unit) {
    val favs = vm.favs
    val rec = vm.recent
    // Agrupamento e filtros rodam em segundo plano: com milhares de títulos, na thread principal travava a tela.
    val cats by produceState(emptyList<Pair<String, Int>>(), items) {
        value = withContext(Dispatchers.Default) { items.groupingBy { it.cat }.eachCount().toList().sortedBy { it.first } }
    }
    var qd by remember { mutableStateOf(st.q) }
    LaunchedEffect(st.q) { if (st.q.isNotEmpty()) delay(180); qd = st.q }
    val needle = remember(qd) { Brand.fold(qd.trim()) }
    val side = st.side
    val view by produceState(CatView(emptyList(), 0, 0), items, side, needle, favs, rec) {
        value = withContext(Dispatchers.Default) {
            val favC = items.count { it.key in favs }
            val recL = if (rec.isEmpty()) emptyList() else { val m = items.associateBy { it.key }; rec.mapNotNull { m[it] } }
            val base = when (side) {
                "" -> items
                "__rec" -> recL
                "__fav" -> items.filter { it.key in favs }
                else -> items.filter { it.cat == side }
            }
            CatView(if (needle.isEmpty()) base else base.filter { it.search.contains(needle) }, recL.size, favC)
        }
    }
    val header = when (side) { "" -> "$title • Todos"; "__rec" -> "Continuar assistindo"; "__fav" -> "Favoritos"; else -> side }

    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(230.dp).fillMaxHeight()) {
            SearchField(st.q, { st.q = it }, "Buscar", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                item(key = "all") { SideRow("Todos", items.size, side == "") { st.side = "" } }
                item(key = "rec") { SideRow("Continuar assistindo", view.recCount, side == "__rec") { st.side = "__rec" } }
                item(key = "fav") { SideRow("Favoritos", view.favCount, side == "__fav") { st.side = "__fav" } }
                items(cats, key = { "c:" + it.first }) { (c, n) -> SideRow(c, n, side == c) { st.side = c } }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(header, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            if (items.isEmpty()) {
                if (vm.loading) SkeletonRows(4) else Text(empty, color = Color.White)
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(view.shown, key = { it.key }) { c -> PosterCard(c.title, c.img, Modifier.fillMaxWidth()) { onOpen(c) } }
            }
        }
    }
}

@Composable
fun VodDetail(vm: AppVM, m: Vod, onPlay: (Boolean, Play) -> Unit, onBack: () -> Unit) {
    var info by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(m.id) {
        info = withContext(Dispatchers.IO) { runCatching { Xtream(vm.provider).vodInfo(m.id) }.getOrDefault(emptyMap()) }
    }
    val key = "V:${m.id}"
    val i = info.orEmpty()
    Row(Modifier.fillMaxSize().padding(16.dp).fadeInOnEnter()) {
        AsyncImage(
            model = m.poster.ifBlank { null }, contentDescription = m.name, contentScale = ContentScale.Crop,
            modifier = Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
        )
        Column(Modifier.padding(start = 16.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = onBack) { Text("← Voltar", color = Color.White) }
            Text(m.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            val meta = listOfNotNull(
                (i["rating"] ?: m.rating).takeIf { it.isNotBlank() }?.let { "★ $it" },
                i["releasedate"], i["duration"], i["genre"]
            ).joinToString("  •  ")
            if (meta.isNotBlank()) Text(meta, color = Color.White, style = MaterialTheme.typography.bodySmall)
            if (info == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            i["plot"]?.let { Text(it, color = Color.White, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) }
            i["cast"]?.let { Text("Elenco: $it", color = Color.White, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
            i["director"]?.let { Text("Direção: $it", color = Color.White, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlay(m.adult, Play(m.name, m.url, key)) }) { Text("▶ Assistir") }
                OutlinedButton(onClick = { vm.toggleFav(key) }) { Text(if (key in vm.favs) "★ Favorito" else "☆ Favoritar", color = Color.White) }
            }
        }
    }
}

@Composable
fun SeriesDetail(vm: AppVM, s: Serie, onPlay: (Boolean, Play) -> Unit, onBack: () -> Unit) {
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(s) {
        val r = withContext(Dispatchers.IO) { runCatching { Xtream(vm.provider).episodes(s.id) }.getOrDefault(emptyList()) }
        eps = r
        season = r.firstOrNull()?.season
    }
    val key = "S:${s.id}"
    Row(Modifier.fillMaxSize().padding(16.dp).fadeInOnEnter()) {
        Column(Modifier.width(260.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = onBack) { Text("← Voltar", color = Color.White) }
            AsyncImage(
                model = s.cover.ifBlank { null }, contentDescription = s.name, contentScale = ContentScale.Crop,
                modifier = Modifier.width(140.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
            )
            Text(s.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(s.plot, color = Color.White, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.toggleFav(key) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (key in vm.favs) "★ Favorita" else "☆ Favoritar", color = Color.White)
            }
        }
        Spacer(Modifier.width(16.dp))
        val list = eps
        LazyColumn(Modifier.weight(1f).background(Glass, RoundedCornerShape(12.dp)).padding(8.dp)) {
            if (list == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (list.isEmpty()) {
                item { Text("Nenhum episódio encontrado.", color = Color.White, modifier = Modifier.padding(16.dp)) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list.map { it.season }.distinct()) { n ->
                            FilterChip(selected = season == n, onClick = { season = n }, label = { Text("Temporada $n") })
                        }
                    }
                }
                items(list.filter { it.season == season }) { e ->
                    val r = RoundedCornerShape(8.dp)
                    Text(
                        "E${e.num}  ${e.title}", color = Color.White,
                        modifier = Modifier.fillMaxWidth().tvFocus(r)
                            .clickable { onPlay(s.adult, Play("${s.name} T${e.season}E${e.num}", e.url, key)) }.padding(14.dp)
                    )
                }
            }
        }
    }
}
