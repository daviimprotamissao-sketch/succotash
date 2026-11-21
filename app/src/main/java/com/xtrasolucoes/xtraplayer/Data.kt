// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
package com.xtrasolucoes.xtraplayer

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.Reader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

const val APP_NAME = "xTra Player"
const val AUTHOR = "xTra Soluções"

/**
 * URL do Firebase Realtime Database usado para sincronizar playlists pelo endereço MAC do aparelho
 * com o site (pasta cloud-playlists/, feito para hospedar no GitHub Pages). Troque pela URL do SEU
 * banco (sem barra no final) — veja cloud-playlists/README.md para o passo a passo.
 * Ex.: "https://xtra-player-default-rtdb.firebaseio.com"
 */
const val CLOUD_DB_URL = "https://console.firebase.google.com/project/xtrasoluctions/database/xtrasoluctions-default-rtdb/data/~2F"

data class Provider(val id: String, val name: String, val type: String, val server: String, val user: String, val pass: String) {
    val base: String get() = server.trim().trimEnd('/')
}
data class Profile(val id: String, val name: String, val kids: Boolean)
class Live(val id: String, val name: String, val logo: String, val group: String, val url: String, val adult: Boolean) {
    /** Nome sem a resolução "(720p)" — calculado uma vez só (antes era um Regex novo a cada acesso). */
    val display: String = Brand.display(name)
    /** Nome sem acento e em minúsculas, pronto para a busca. */
    val search: String = Brand.fold(name)
}
class Vod(val id: String, val name: String, val poster: String, val category: String, val url: String, val adult: Boolean, val rating: String = "")
data class Prog(val title: String, val start: Long, val stop: Long, val desc: String)
class Serie(val id: String, val name: String, val cover: String, val category: String, val plot: String, val adult: Boolean)
data class Episode(val title: String, val season: Int, val num: Int, val url: String)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val builtin = Provider("builtin", "Lista padrão", "builtin", "", "", "")

    var providers: List<Provider>
        get() = runCatching {
            val a = JSONArray(sp.getString("providers", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Provider(o.getString("id"), o.getString("name"), o.getString("type"), o.optString("server"), o.optString("user"), o.optString("pass"))
            }
        }.getOrDefault(emptyList()).let { listOf(builtin) + it }
        set(v) {
            val arr = JSONArray(v.filter { it.id != "builtin" }.map {
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("server", it.server).put("user", it.user).put("pass", it.pass)
            })
            sp.edit().putString("providers", arr.toString()).apply()
        }

    var profiles: List<Profile>
        get() = runCatching {
            val a = JSONArray(sp.getString("profiles", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Profile(o.getString("id"), o.getString("name"), o.optBoolean("kids"))
            }
        }.getOrDefault(emptyList()).ifEmpty { listOf(Profile("p1", "Principal", false)) }
        set(v) {
            val arr = JSONArray(v.map { JSONObject().put("id", it.id).put("name", it.name).put("kids", it.kids) })
            sp.edit().putString("profiles", arr.toString()).apply()
        }

    var activeProvider: String
        get() = sp.getString("ap", "builtin") ?: "builtin"
        set(v) { sp.edit().putString("ap", v).apply() }
    var aspect: Int
        get() = sp.getInt("aspect", 0)
        set(v) { sp.edit().putInt("aspect", v).apply() }
    var pin: String
        get() = sp.getString("pin", "") ?: ""
        set(v) { sp.edit().putString("pin", v).apply() }
    var parental: Boolean
        get() = sp.getBoolean("parental", true)
        set(v) { sp.edit().putBoolean("parental", v).apply() }

    fun recent(pid: String): List<String> = (sp.getString("rec_$pid", "") ?: "").split(",").filter { it.isNotBlank() }
    fun setRecent(pid: String, l: List<String>) { sp.edit().putString("rec_$pid", l.joinToString(",")).apply() }

    fun favs(profileId: String): Set<String> = sp.getStringSet("fav_$profileId", emptySet()) ?: emptySet()
    fun setFavs(profileId: String, s: Set<String>) { sp.edit().putStringSet("fav_$profileId", s.toSet()).apply() }

    /**
     * Endereço MAC "de identidade" deste aparelho. O Android moderno não deixa mais nenhum app ler o
     * MAC real do Wi-Fi (por privacidade, desde o Android 6, e nem com permissão desde o Android 10),
     * então geramos um endereço só nosso (formato AA:BB:CC:DD:EE:FF), uma vez só, e guardamos —
     * exatamente como fazem os sistemas de ativação "por MAC" (tipo MAG/Stalker) que o site usa como chave.
     */
    fun deviceMac(): String {
        sp.getString("device_mac", null)?.let { return it }
        val b = ByteArray(6).also { java.security.SecureRandom().nextBytes(it) }
        b[0] = (b[0].toInt() and 0xFE or 0x02).toByte() // bit "administrado localmente": nunca colide com um MAC de fábrica real
        val mac = b.joinToString(":") { "%02X".format(it) }
        sp.edit().putString("device_mac", mac).apply()
        return mac
    }
}

object Net {
    private fun open(url: String, method: String = "GET"): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.requestMethod = method
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) xTraPlayer")
        return c
    }

    /** Respostas pequenas (auth, EPG, info). O Android já pede e descompacta gzip sozinho. */
    fun get(url: String): String {
        val c = open(url)
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    /** Respostas grandes: entrega um Reader para processar aos poucos, sem montar a String inteira na memória. */
    fun <T> stream(url: String, block: (Reader) -> T): T {
        val c = open(url)
        try { return InputStreamReader(c.inputStream, Charsets.UTF_8).buffered(64 * 1024).use(block) } finally { c.disconnect() }
    }

    /** POST (cria um item com chave própria) ou PUT (substitui o valor inteiro) com corpo JSON. Usado pela sincronização de playlists. */
    fun send(url: String, method: String, body: String): String {
        val c = open(url, method)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    fun delete(url: String) {
        val c = open(url, "DELETE")
        try { c.inputStream.bufferedReader().use { it.readText() } } catch (e: Exception) { } finally { c.disconnect() }
    }
}

data class CloudPlaylist(val key: String, val name: String, val type: String, val server: String, val user: String, val pass: String)

/**
 * Sincroniza playlists usando o endereço MAC do aparelho como chave — sem SDK nenhum, só HTTP puro
 * (igual ao resto do app), falando direto com o Firebase Realtime Database configurado em CLOUD_DB_URL.
 * O site em cloud-playlists/ lê e escreve exatamente no mesmo lugar: o que você adicionar/remover em um
 * dos dois lados aparece no outro (no app, ao abrir a aba Playlists ou tocar em "Sincronizar agora").
 */
object Cloud {
    val configured: Boolean get() = CLOUD_DB_URL.isNotBlank() && !CLOUD_DB_URL.contains("SEU-PROJETO")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun base(mac: String) = "${CLOUD_DB_URL.trimEnd('/')}/playlists/${enc(mac)}/items"

    fun list(mac: String): List<CloudPlaylist> {
        if (!configured) return emptyList()
        val body = Net.get("${base(mac)}.json")
        if (body.isBlank() || body == "null") return emptyList()
        val obj = JSONObject(body)
        return obj.keys().asSequence().map { k ->
            val o = obj.getJSONObject(k)
            CloudPlaylist(k, o.optString("name"), o.optString("type", "m3u"), o.optString("server"), o.optString("user"), o.optString("pass"))
        }.sortedBy { it.name.lowercase() }.toList()
    }

    fun add(mac: String, name: String, type: String, server: String, user: String, pass: String) {
        if (!configured) return
        val body = JSONObject().put("name", name).put("type", type).put("server", server).put("user", user).put("pass", pass)
        Net.send("${base(mac)}.json", "POST", body.toString())
    }

    fun remove(mac: String, key: String) {
        if (!configured) return
        Net.delete("${base(mac)}/$key.json")
    }
}

object Brand {
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val rules = listOf(
        "Band" to r("^band"),
        "Record" to r("^record"),
        "SBT" to r("^sbt"),
        "Globo" to r("^(rede globo|tv globo|globo|gnt|gloob|sportv|combate|multishow)"),
        "Telecine" to r("^telecine"),
        "RedeTV!" to r("^rede ?tv"),
        "CNT" to r("^(rede )?cnt"),
        "Públicos" to r("^(tv brasil|canal gov|canal educacao|canal saude|tv cultura|rede minas|tve|canal futura|futura|sesc tv|nbr)"),
        "Legislativo e Justiça" to r("^(tv camara|tv senado|tv justica|tv assembleia)"),
        "ESPN" to r("^espn"),
        "Premiere" to r("^premiere"),
        "HBO" to r("^(hbo|max\\b)"),
        "Disney" to r("^(disney|playhouse)"),
        "Discovery" to r("^(discovery|tlc|animal planet|hgtv|food network)"),
        "A+E" to r("^(a&e|history|lifetime)"),
        "Warner" to r("^(warner|tnt|tcm|space)"),
        "Paramount" to r("^paramount"),
        "Sony" to r("^(sony|axn)"),
        "MTV" to r("mtv"),
        "Comedy Central" to r("^(comedy central|south park)"),
        "Nickelodeon" to r("^nick"),
        "Cartoon" to r("^(cartoon|tooncast|boomerang)"),
        "Pluto TV" to r("pluto tv"),
    )
    private val order = rules.map { it.first }
    private val adultRx = r("adult|xxx|\\+18|18\\+|erotic|porn|sexy|playboy|sexprive|sex hot|hot ?club|venus|brasileirinhas")

    // Antes estes Regex eram recriados a CADA canal (milhares de vezes). Agora são compilados uma vez.
    private val marks = Regex("\\p{M}+")
    private val prefixRx = r("^[\\[(|\\s]*(br|brasil|bra|pt)\\s*[\\])|:\\-]+\\s*")
    private val resolutionRx = Regex("\\s*\\((\\d{3,4}[pi])\\)")

    fun norm(s: String): String {
        var ascii = true
        for (c in s) if (c.code > 127) { ascii = false; break }
        return if (ascii) s else Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "")
    }
    fun fold(s: String): String = norm(s).lowercase()
    fun display(name: String): String = name.replace(resolutionRx, "").trim()

    private fun clean(name: String) = norm(name).replace(prefixRx, "").trim()
    fun of(name: String, fallback: String): String {
        val n = clean(name)
        for ((g, rx) in rules) if (rx.containsMatchIn(n)) return g
        return fallback.ifBlank { "Outros" }
    }
    fun rank(g: String): Int { val i = order.indexOf(g); return if (i < 0) 999 else i }
    fun isAdult(vararg s: String) = s.any { adultRx.containsMatchIn(norm(it)) }
}

/** Garante ids únicos (dois canais com a mesma URL derrubavam o app: chave repetida na lista). */
private fun uniqueId(seen: HashSet<String>, base: String): String {
    if (seen.add(base)) return base
    var n = 2
    while (!seen.add("$base#$n")) n++
    return "$base#$n"
}

object M3u {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")
    fun parse(text: String): List<Live> = parse(text.lineSequence())
    fun parse(lines: Sequence<String>): List<Live> {
        val out = ArrayList<Live>()
        val seen = HashSet<String>()
        var info: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) info = line
            else if (line.startsWith("#")) continue
            else {
                val i = info
                if (i != null) {
                    val a = HashMap<String, String>()
                    for (m in attr.findAll(i)) a[m.groupValues[1]] = m.groupValues[2]
                    val idx = i.lastIndexOf("\",")
                    val name = (if (idx >= 0) i.substring(idx + 2) else i.substringAfter(',', "")).trim().ifBlank { "Canal" }
                    val cat = a["group-title"].orEmpty().split(';').first()
                    out.add(Live(uniqueId(seen, line.hashCode().toString()), name, a["tvg-logo"].orEmpty(), Brand.of(name, cat), line, Brand.isAdult(name, cat)))
                    info = null
                }
            }
        }
        return out
    }
}

/** Cache em disco das listas: na próxima abertura o app mostra tudo na hora e atualiza em segundo plano. */
object Cache {
    private fun file(dir: File, kind: String, pid: String) = File(dir, "c_${kind}_${pid.filter { it.isLetterOrDigit() }}.tsv")
    private fun s(x: String) = x.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private inline fun save(dir: File, kind: String, pid: String, write: (java.io.BufferedWriter) -> Unit) {
        try {
            val tmp = File(dir, "c_${kind}.tmp")
            tmp.bufferedWriter(Charsets.UTF_8, 64 * 1024).use { w -> write(w) }
            tmp.renameTo(file(dir, kind, pid))
        } catch (e: Exception) { /* cache é opcional */ }
    }

    private inline fun <T> read(dir: File, kind: String, pid: String, min: Int, make: (List<String>) -> T): List<T> {
        val f = file(dir, kind, pid)
        if (!f.exists()) return emptyList()
        val out = ArrayList<T>()
        try {
            f.bufferedReader(Charsets.UTF_8, 64 * 1024).useLines { seq ->
                for (line in seq) {
                    val p = line.split('\t')
                    if (p.size >= min) out.add(make(p))
                }
            }
        } catch (e: Exception) { return emptyList() }
        return out
    }

    fun saveLive(dir: File, pid: String, l: List<Live>) = save(dir, "live", pid) { w ->
        for (c in l) { w.write("${s(c.id)}\t${s(c.name)}\t${s(c.logo)}\t${s(c.group)}\t${s(c.url)}\t${if (c.adult) 1 else 0}\n") }
    }
    fun live(dir: File, pid: String): List<Live> = read(dir, "live", pid, 6) { p -> Live(p[0], p[1], p[2], p[3], p[4], p[5] == "1") }

    fun saveVod(dir: File, pid: String, l: List<Vod>) = save(dir, "vod", pid) { w ->
        for (c in l) { w.write("${s(c.id)}\t${s(c.name)}\t${s(c.poster)}\t${s(c.category)}\t${s(c.url)}\t${if (c.adult) 1 else 0}\t${s(c.rating)}\n") }
    }
    fun vod(dir: File, pid: String): List<Vod> = read(dir, "vod", pid, 7) { p -> Vod(p[0], p[1], p[2], p[3], p[4], p[5] == "1", p[6]) }

    fun saveSeries(dir: File, pid: String, l: List<Serie>) = save(dir, "series", pid) { w ->
        for (c in l) { w.write("${s(c.id)}\t${s(c.name)}\t${s(c.cover)}\t${s(c.category)}\t${s(c.plot)}\t${if (c.adult) 1 else 0}\n") }
    }
    fun series(dir: File, pid: String): List<Serie> = read(dir, "series", pid, 6) { p -> Serie(p[0], p[1], p[2], p[3], p[4], p[5] == "1") }
}

class Xtream(private val p: Provider) {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val root get() = "${p.base}/player_api.php?username=${enc(p.user)}&password=${enc(p.pass)}"
    private fun api(action: String, extra: String = "") = Net.get("$root&action=$action$extra")

    fun auth(): Boolean = JSONObject(Net.get(root)).optJSONObject("user_info")?.optInt("auth", 0) == 1

    private fun JsonReader.str(): String = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.NULL -> { nextNull(); "" }
        else -> { skipValue(); "" }
    }

    /**
     * Lê o array JSON aos poucos (streaming), guardando só os campos necessários.
     * Antes: a resposta inteira (dezenas de MB em listas grandes) virava uma String e depois um JSONArray,
     * estourando a memória do app — e como OutOfMemoryError não é Exception, o app fechava sozinho.
     */
    private fun each(action: String, wanted: Set<String>, onItem: (Map<String, String>) -> Unit) {
        Net.stream("$root&action=$action") { r ->
            val rd = JsonReader(r)
            if (rd.peek() != JsonToken.BEGIN_ARRAY) { rd.skipValue(); return@stream }
            rd.beginArray()
            while (rd.hasNext()) {
                if (rd.peek() != JsonToken.BEGIN_OBJECT) { rd.skipValue(); continue }
                val m = HashMap<String, String>(10)
                rd.beginObject()
                while (rd.hasNext()) {
                    val n = rd.nextName()
                    if (n in wanted) m[n] = rd.str() else rd.skipValue()
                }
                rd.endObject()
                onItem(m)
            }
            rd.endArray()
        }
    }

    fun cats(action: String): Map<String, String> {
        val out = HashMap<String, String>()
        each(action, setOf("category_id", "category_name")) { out[it["category_id"].orEmpty()] = it["category_name"].orEmpty() }
        return out
    }

    fun liveRaw(): List<Array<String>> {
        val out = ArrayList<Array<String>>()
        each("get_live_streams", setOf("stream_id", "name", "stream_icon", "category_id")) {
            out.add(arrayOf(it["stream_id"].orEmpty(), it["name"].orEmpty(), it["stream_icon"].orEmpty(), it["category_id"].orEmpty()))
        }
        return out
    }
    fun liveFrom(raw: List<Array<String>>, cats: Map<String, String>): List<Live> {
        val seen = HashSet<String>(raw.size * 2)
        val out = ArrayList<Live>(raw.size)
        for (r in raw) {
            if (r[0].isEmpty()) continue
            val cat = cats[r[3]] ?: "Outros"
            val id = uniqueId(seen, r[0])
            out.add(Live(id, r[1], r[2], Brand.of(r[1], cat), "${p.base}/live/${p.user}/${p.pass}/${r[0]}.m3u8", Brand.isAdult(r[1], cat)))
        }
        return out
    }

    fun vodRaw(): List<Array<String>> {
        val out = ArrayList<Array<String>>()
        each("get_vod_streams", setOf("stream_id", "name", "stream_icon", "category_id", "container_extension", "rating")) {
            out.add(arrayOf(it["stream_id"].orEmpty(), it["name"].orEmpty(), it["stream_icon"].orEmpty(), it["category_id"].orEmpty(),
                it["container_extension"].orEmpty().ifBlank { "mp4" }, it["rating"].orEmpty()))
        }
        return out
    }
    fun vodFrom(raw: List<Array<String>>, cats: Map<String, String>): List<Vod> {
        val seen = HashSet<String>(raw.size * 2)
        val out = ArrayList<Vod>(raw.size)
        for (r in raw) {
            if (r[0].isEmpty()) continue
            val cat = cats[r[3]] ?: "Outros"
            out.add(Vod(uniqueId(seen, r[0]), r[1], r[2], cat, "${p.base}/movie/${p.user}/${p.pass}/${r[0]}.${r[4]}", Brand.isAdult(r[1], cat), r[5]))
        }
        return out
    }

    fun seriesRaw(): List<Array<String>> {
        val out = ArrayList<Array<String>>()
        each("get_series", setOf("series_id", "name", "cover", "category_id", "plot")) {
            out.add(arrayOf(it["series_id"].orEmpty(), it["name"].orEmpty(), it["cover"].orEmpty(), it["category_id"].orEmpty(), it["plot"].orEmpty()))
        }
        return out
    }
    fun seriesFrom(raw: List<Array<String>>, cats: Map<String, String>): List<Serie> {
        val seen = HashSet<String>(raw.size * 2)
        val out = ArrayList<Serie>(raw.size)
        for (r in raw) {
            if (r[0].isEmpty()) continue
            val cat = cats[r[3]] ?: "Outros"
            out.add(Serie(uniqueId(seen, r[0]), r[1], r[2], cat, r[4], Brand.isAdult(r[1], cat)))
        }
        return out
    }

    private fun b64(s: String) = try { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) } catch (e: Exception) { s }

    fun epg(streamId: String): List<Prog> {
        val a = JSONObject(api("get_simple_data_table", "&stream_id=$streamId")).optJSONArray("epg_listings") ?: return emptyList()
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Prog(b64(o.optString("title")), o.optLong("start_timestamp"), o.optLong("stop_timestamp"), b64(o.optString("description")))
        }
    }

    fun vodInfo(id: String): Map<String, String> {
        val i = JSONObject(api("get_vod_info", "&vod_id=$id")).optJSONObject("info") ?: return emptyMap()
        return listOf("plot", "genre", "releasedate", "rating", "duration", "cast", "director")
            .associateWith { i.optString(it) }.filterValues { it.isNotBlank() && it != "null" }
    }

    fun episodes(seriesId: String): List<Episode> {
        val j = JSONObject(api("get_series_info", "&series_id=$seriesId"))
        val eps = j.optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<Episode>()
        for (k in eps.keys()) {
            val season = k.toIntOrNull() ?: 0
            val a = eps.optJSONArray(k) ?: continue
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                out.add(Episode(o.optString("title", "Episódio ${i + 1}"), season, o.optInt("episode_num", i + 1),
                    "${p.base}/series/${p.user}/${p.pass}/${o.optString("id")}.$ext"))
            }
        }
        return out.sortedWith(compareBy<Episode>({ it.season }, { it.num }))
    }
}
