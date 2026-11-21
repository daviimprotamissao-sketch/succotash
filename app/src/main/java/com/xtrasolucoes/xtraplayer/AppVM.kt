// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
package com.xtrasolucoes.xtraplayer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class CatState {
    var q by mutableStateOf("")
    var side by mutableStateOf("")
}

class Group(val name: String, val list: List<Live>)

class LiveData(val chans: List<Live>, val groups: List<Group>) {
    companion object { val EMPTY = LiveData(emptyList(), emptyList()) }
}

/** Item de filme/série já pronto para a grade (montado fora da thread principal). */
class CatItem(val key: String, val title: String, val img: String, val cat: String, val adult: Boolean, val ref: Any) {
    val search: String = Brand.fold(title)
}

class AppVM(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()
    var firebaseUser by mutableStateOf<FirebaseUser?>(auth.currentUser)
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    var isAdmin by mutableStateOf(false)
        private set

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) { authError = "Informe e-mail e senha."; return }
        viewModelScope.launch {
            authBusy = true; authError = null
            try { auth.signInWithEmailAndPassword(email.trim(), password).await() }
            catch (t: Throwable) { authError = t.message ?: "Não foi possível entrar." }
            finally { authBusy = false }
        }
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.length < 6) { authError = "Informe um e-mail válido e senha com pelo menos 6 caracteres."; return }
        viewModelScope.launch {
            authBusy = true; authError = null
            try {
                val credential = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                credential.user?.let { user ->
                    runCatching {
                        FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("profile")
                            .setValue(mapOf(
                                "name" to (user.email?.substringBefore("@") ?: "Membro"),
                                "email" to user.email,
                                "createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )).await()
                    }
                }
            } catch (t: Throwable) { authError = t.message ?: "Não foi possível criar a conta." }
            finally { authBusy = false }
        }
    }

    fun signOut() { authError = null; auth.signOut() }

    private suspend fun refreshRole(uid: String) {
        isAdmin = try {
            FirebaseDatabase.getInstance().getReference("admins").child(uid).get().await().getValue(Boolean::class.java) == true
        } catch (_: Throwable) { false }
    }

    val store = Store(app)
    private val dir = app.filesDir

    var providers by mutableStateOf(store.providers)
    var profiles by mutableStateOf(store.profiles)
    var profile by mutableStateOf<Profile?>(null)
    var provider by mutableStateOf(providers.firstOrNull { it.id == store.activeProvider } ?: providers.first())

    var live by mutableStateOf<List<Live>>(emptyList())
    var vod by mutableStateOf<List<Vod>>(emptyList())
    var series by mutableStateOf<List<Serie>>(emptyList())

    /** Listas já filtradas/agrupadas para o perfil atual (calculadas em segundo plano). */
    var liveData by mutableStateOf(LiveData.EMPTY)
    var vodItems by mutableStateOf<List<CatItem>>(emptyList())
    var serItems by mutableStateOf<List<CatItem>>(emptyList())

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var favs by mutableStateOf<Set<String>>(emptySet())
    var unlocked by mutableStateOf(false)
    var parental by mutableStateOf(store.parental)
    var hasPin by mutableStateOf(store.pin.isNotEmpty())
    var aspect by mutableStateOf(store.aspect)
    var recent by mutableStateOf<List<String>>(emptyList())
    var liveGroup by mutableStateOf<String?>(null)
    var liveSel by mutableStateOf<Live?>(null)
    val vodUi = CatState()
    val serUi = CatState()

    // Playlists na nuvem (aba Playlists, sincronizadas pelo MAC com o site)
    val deviceMac: String = store.deviceMac()
    var cloudPlaylists by mutableStateOf<List<CloudPlaylist>>(emptyList())
    var cloudLoading by mutableStateOf(false)
    var cloudError by mutableStateOf<String?>(null)

    private var job: Job? = null
    private var extraJob: Job? = null
    private var buildJob: Job? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firebaseUser = firebaseAuth.currentUser
            authError = null
            isAdmin = false
            val user = firebaseAuth.currentUser
            if (user != null) viewModelScope.launch {
                refreshRole(user.uid)
                loadCloud()
            } else {
                cloudPlaylists = emptyList()
                providers = providers.filterNot { it.id.startsWith("cloud_") }
                store.providers = providers
                if (provider.id.startsWith("cloud_")) {
                    provider = providers.first()
                    store.activeProvider = provider.id
                }
            }
        }

    init {
        providers = store.providers.filterNot { it.id.startsWith("cloud_") }
        store.providers = providers
        auth.addAuthStateListener(authListener)
        load()
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }

    /**
     * Carrega a lista:
     *  1) mostra na hora o que está salvo em disco (se houver);
     *  2) baixa a lista de canais (autenticação + categorias + canais em paralelo, lendo em streaming);
     *  3) filmes e séries baixam em segundo plano, sem travar os canais.
     */
    fun load() {
        job?.cancel(); extraJob?.cancel()
        val p = provider
        error = null
        loading = true
        job = viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { Triple(Cache.live(dir, p.id), Cache.vod(dir, p.id), Cache.series(dir, p.id)) }
                live = cached.first; vod = cached.second; series = cached.third
                liveSel = null; liveGroup = null
                rebuild()

                val fresh = withContext(Dispatchers.IO) { fetchLive(p) }
                live = fresh
                rebuild()
                loading = false
                withContext(Dispatchers.IO) { Cache.saveLive(dir, p.id, fresh) }

                if (p.type == "xtream") loadExtras(p)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Throwable (e não só Exception): assim um OutOfMemoryError não derruba mais o app.
                loading = false
                error = if (live.isEmpty()) "Falha ao carregar: ${t.message ?: "erro de conexão"}"
                else "Sem conexão com o provedor. Mostrando a lista salva."
            }
        }
    }

    private suspend fun fetchLive(p: Provider): List<Live> = when (p.type) {
        "xtream" -> coroutineScope {
            val x = Xtream(p)
            val auth = async(Dispatchers.IO) { x.auth() }
            val cats = async(Dispatchers.IO) { x.cats("get_live_categories") }
            val raw = async(Dispatchers.IO) { x.liveRaw() }
            if (!auth.await()) throw Exception("usuário ou senha inválidos")
            x.liveFrom(raw.await(), cats.await())
        }
        "m3u" -> Net.stream(p.server) { r -> M3u.parse(r.buffered().lineSequence()) }
        else -> getApplication<Application>().assets.open("playlist.txt").bufferedReader().use { M3u.parse(it.readText()) }
    }

    private fun loadExtras(p: Provider) {
        extraJob?.cancel()
        extraJob = viewModelScope.launch {
            launch {
                val r = withContext(Dispatchers.IO) { safe { val x = Xtream(p); x.vodFrom(x.vodRaw(), x.cats("get_vod_categories")) } }
                if (r != null && provider.id == p.id) {
                    vod = r; rebuild()
                    withContext(Dispatchers.IO) { Cache.saveVod(dir, p.id, r) }
                }
            }
            launch {
                val r = withContext(Dispatchers.IO) { safe { val x = Xtream(p); x.seriesFrom(x.seriesRaw(), x.cats("get_series_categories")) } }
                if (r != null && provider.id == p.id) {
                    series = r; rebuild()
                    withContext(Dispatchers.IO) { Cache.saveSeries(dir, p.id, r) }
                }
            }
        }
    }

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (e: CancellationException) { throw e } catch (t: Throwable) { null }

    /** Filtra (perfil infantil), agrupa e ordena fora da thread principal. */
    fun rebuild() {
        val l = live; val v = vod; val s = series
        val kids = profile?.kids == true
        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            val r = withContext(Dispatchers.Default) {
                val ch = (if (kids) l.filter { !it.adult } else l).distinctBy { it.id }
                val groups = ch.groupBy { it.group }.map { Group(it.key, it.value) }
                    .sortedWith(compareBy<Group>({ Brand.rank(it.name) }, { it.name }))
                val vi = v.filter { !(kids && it.adult) }.map { CatItem("V:${it.id}", it.name, it.poster, it.category, it.adult, it) }.distinctBy { it.key }
                val si = s.filter { !(kids && it.adult) }.map { CatItem("S:${it.id}", it.name, it.cover, it.category, it.adult, it) }.distinctBy { it.key }
                Triple(LiveData(ch, groups), vi, si)
            }
            liveData = r.first; vodItems = r.second; serItems = r.third
        }
    }

    @JvmName("applyAspect")
    fun setAspect(i: Int) { aspect = i; store.aspect = i }

    // Perfis
    fun selectProfile(p: Profile) {
        profile = p; unlocked = false; favs = store.favs(p.id); recent = store.recent(p.id); liveGroup = null; liveSel = null
        rebuild()
    }
    fun switchProfile() { profile = null }
    fun addProfile(name: String, kids: Boolean) {
        profiles = profiles + Profile("p" + System.currentTimeMillis(), name, kids)
        store.profiles = profiles
    }
    fun setKids(p: Profile, kids: Boolean) {
        profiles = profiles.map { if (it.id == p.id) it.copy(kids = kids) else it }
        store.profiles = profiles
        if (profile?.id == p.id) { profile = profiles.first { it.id == p.id }; rebuild() }
    }
    fun deleteProfile(p: Profile) {
        if (profiles.size <= 1) return
        profiles = profiles.filter { it.id != p.id }
        store.profiles = profiles
    }

    // Provedores
    fun selectProvider(p: Provider) { provider = p; store.activeProvider = p.id; load() }
    fun addProvider(p: Provider) {
        providers = providers + p
        store.providers = providers
        selectProvider(p)
    }
    fun deleteProvider(p: Provider) {
        if (p.id == "builtin") return
        providers = providers.filter { it.id != p.id }
        store.providers = providers
        if (provider.id == p.id) selectProvider(providers.first())
    }

    // Playlists na nuvem vinculadas à conta Firebase (mesmo UID usado pelo site).
    private fun cloudId(key: String) = "cloud_$key"

    fun loadCloud() {
        val uid = firebaseUser?.uid
        if (uid == null) { cloudPlaylists = emptyList(); cloudError = "Entre na sua conta para sincronizar."; return }
        cloudLoading = true; cloudError = null
        viewModelScope.launch {
            val result = runCatching { Cloud.list(uid) }
            cloudLoading = false
            val r = result.getOrElse { cloudError = "Falha ao sincronizar: " + (it.message ?: "verifique sua conexão"); return@launch }
            cloudPlaylists = r
            val cloudIds = r.map { cloudId(it.key) }.toSet()
            val toAdd = r.filter { cloudId(it.key) !in providers.map { p -> p.id } }
                .map { Provider(cloudId(it.key), it.name, it.type, it.server, it.user, it.pass) }
            val kept = providers.filter { it.id == "builtin" || !it.id.startsWith("cloud_") || it.id in cloudIds }
            if (toAdd.isNotEmpty() || kept.size != providers.size) {
                providers = kept + toAdd
                if (provider.id !in providers.map { it.id }) selectProvider(providers.first())
            }
        }
    }

    fun addCloudPlaylist(name: String, type: String, server: String, user: String, pass: String) {
        val uid = firebaseUser?.uid ?: run { cloudError = "Entre na sua conta primeiro."; return }
        cloudLoading = true; cloudError = null
        viewModelScope.launch {
            val result = runCatching { Cloud.add(uid, name, type, server, user, pass) }
            if (result.isFailure) cloudError = "Não foi possível salvar a playlist."
            loadCloud()
        }
    }

    fun removeCloudPlaylist(p: CloudPlaylist) {
        val uid = firebaseUser?.uid ?: return
        cloudLoading = true
        viewModelScope.launch {
            val result = runCatching { Cloud.remove(uid, p.key) }
            if (result.isFailure) cloudError = "Não foi possível remover a playlist."
            providers.firstOrNull { it.id == cloudId(p.key) }?.let { deleteProvider(it) }
            loadCloud()
        }
    }

    fun touch(key: String) {
        val p = profile ?: return
        recent = (listOf(key) + recent.filter { it != key }).take(30)
        store.setRecent(p.id, recent)
    }

    // Favoritos
    fun toggleFav(id: String) {
        val p = profile ?: return
        favs = if (id in favs) favs - id else favs + id
        store.setFavs(p.id, favs)
    }

    // Controle parental
    fun hidden(adult: Boolean) = adult && profile?.kids == true
    fun locked(adult: Boolean) = adult && parental && hasPin && profile?.kids != true && !unlocked
    fun checkPin(pin: String) = pin == store.pin
    fun setPin(pin: String) { store.pin = pin; hasPin = pin.isNotEmpty() }
    @JvmName("applyParental")
    fun setParental(v: Boolean) { parental = v; store.parental = v }
}
