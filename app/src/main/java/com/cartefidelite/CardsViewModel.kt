package com.cartefidelite

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class LoyaltyCard(
    val id: String = UUID.randomUUID().toString(),
    val shop: String,
    val colorIndex: Int = 0,
    val barcode: String = "",
    val imagePath: String = "",
    val format: String = "CODE_128",
)

fun randomBarcode(): String = (1..12).joinToString("") { (0..9).random().toString() }

class CardsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cards", Context.MODE_PRIVATE)
    private val imagesDir = File(app.filesDir, "card_images").apply { mkdirs() }

    val cards = mutableStateListOf<LoyaltyCard>().also { it.addAll(load()) }

    init {
        cards.filter { !it.imagePath.endsWith("_logo.png") || !File(it.imagePath).exists() }.forEach { download(it) }
    }

    private fun open(url: String) = URL(url).openConnection().apply {
        connectTimeout = 10000
        readTimeout = 15000
        setRequestProperty("User-Agent", "CarteFidelite/1.0 (Android)")
    }

    private fun getText(url: String) =
        runCatching { open(url).getInputStream().bufferedReader().use { it.readText() } }.getOrNull()

    // Logo officiel via Wikidata (propriété P154), en testant fr puis en.
    private fun wikidataLogo(shop: String): String? {
        val q = URLEncoder.encode(shop, "UTF-8")
        for (lang in listOf("fr", "en")) {
            val ids = getText(
                "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$q&language=$lang&limit=6&format=json"
            )?.let { runCatching { JSONObject(it).getJSONArray("search") }.getOrNull() } ?: continue
            val list = (0 until ids.length()).map { ids.getJSONObject(it).getString("id") }
            if (list.isEmpty()) continue
            val ents = getText(
                "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${list.joinToString("|")}&props=claims&format=json"
            )?.let { runCatching { JSONObject(it).getJSONObject("entities") }.getOrNull() } ?: continue
            for (id in list) {
                val file = runCatching {
                    ents.getJSONObject(id).getJSONObject("claims").getJSONArray("P154").getJSONObject(0)
                        .getJSONObject("mainsnak").getJSONObject("datavalue").getString("value")
                }.getOrNull() ?: continue
                return "https://commons.wikimedia.org/wiki/Special:FilePath/" +
                    URLEncoder.encode(file, "UTF-8").replace("+", "%20") + "?width=600"
            }
        }
        return null
    }

    // Repli : fichier « logo » portant le nom du magasin sur Wikimedia Commons.
    private fun commonsLogo(shop: String): String? {
        val name = shop.trim()
        val cap = name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        val candidates = listOf(name, cap).distinct().flatMap { n ->
            listOf("$n logo.svg", "$n Logo.svg", "Logo $n.svg", "$n.svg", "$n logo.png", "$n.png")
        }
        for (c in candidates) {
            val url = "https://commons.wikimedia.org/wiki/Special:FilePath/" +
                URLEncoder.encode(c, "UTF-8").replace("+", "%20") + "?width=600"
            val ok = runCatching { (open(url) as java.net.HttpURLConnection).responseCode == 200 }.getOrDefault(false)
            if (ok) return url
        }
        return commonsSearchLogo(shop)
    }

    private fun commonsSearchLogo(shop: String): String? {
        val q = URLEncoder.encode("logo $shop", "UTF-8")
        val arr = getText(
            "https://commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=6&srlimit=15&srsearch=$q&format=json"
        )?.let { runCatching { JSONObject(it).getJSONObject("query").getJSONArray("search") }.getOrNull() } ?: return null
        val key = shop.lowercase().filter { it.isLetterOrDigit() }
        val titles = (0 until arr.length()).map { arr.getJSONObject(it).getString("title") }
            .filter { t ->
                val n = t.removePrefix("File:").lowercase()
                n.contains("logo") && n.filter { it.isLetterOrDigit() }.contains(key) &&
                    (n.endsWith(".svg") || n.endsWith(".png"))
            }
        val best = titles.firstOrNull { !it.lowercase().contains("old") && !it.contains(Regex("(19|20)\\d\\d")) }
            ?: titles.firstOrNull() ?: return null
        return "https://commons.wikimedia.org/wiki/Special:FilePath/" +
            URLEncoder.encode(best.removePrefix("File:"), "UTF-8").replace("+", "%20") + "?width=600"
    }

    // Repli : icône du site du magasin.
    private fun faviconLogo(shop: String): String? {
        val name = shop.lowercase().filter { it.isLetterOrDigit() }
        if (name.isEmpty()) return null
        for (tld in listOf("com", "fr")) {
            val url = "https://www.google.com/s2/favicons?domain=$name.$tld&sz=256"
            val ok = runCatching { (open("https://$name.$tld") as java.net.HttpURLConnection).responseCode in 200..399 }
                .getOrDefault(false)
            if (ok) return url
        }
        return null
    }

    private fun commonsFileUrl(file: String) = "https://commons.wikimedia.org/wiki/Special:FilePath/" +
        URLEncoder.encode(file.removePrefix("File:"), "UTF-8").replace("+", "%20") + "?width=600"

    // Jusqu'à 10 images candidates : logos Wikidata, recherche Wikimedia Commons, favicon.
    suspend fun findImages(shop: String): List<String> = withContext(Dispatchers.IO) {
        val out = LinkedHashSet<String>()
        runCatching {
            val q = URLEncoder.encode(shop, "UTF-8")
            for (lang in listOf("fr", "en")) {
                val ids = getText(
                    "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$q&language=$lang&limit=8&format=json"
                )?.let { runCatching { JSONObject(it).getJSONArray("search") }.getOrNull() } ?: continue
                val list = (0 until ids.length()).map { ids.getJSONObject(it).getString("id") }
                if (list.isEmpty()) continue
                val ents = getText(
                    "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${list.joinToString("|")}                    &props=claims|labels&format=json"
                )?.let { runCatching { JSONObject(it).getJSONObject("entities") }.getOrNull() } ?: continue
                                    val shopKey = shop.lowercase().filter { it.isLetterOrDigit() }
                                    for (id in list) {
                                        val labels = runCatching { ents.getJSONObject(id).getJSONObject("labels") }.getOrNull()
                                        val names = labels?.keys()?.asSequence()?.map { labels.getJSONObject(it).getString("value") }.orEmpty()
                                        if (shopKey.isNotEmpty() && names.none { n -> n.lowercase().filter { it.isLetterOrDigit() }.contains(shopKey) }) continue
                                        val claims = runCatching { ents.getJSONObject(id).getJSONObject("claims").getJSONArray("P154") }
                        .getOrNull() ?: continue
                    for (i in 0 until claims.length()) {
                        runCatching {
                            claims.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue").getString("value")
                        }.getOrNull()?.let { out.add(commonsFileUrl(it)) }
                    }
                }
            }
        }
        val key = shop.lowercase().filter { it.isLetterOrDigit() }
        if (key.isNotEmpty()) for (term in listOf("logo $shop", "$shop logo svg")) {
            if (out.size >= 10) break
            val arr = getText(
                "https://commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=6&srlimit=30&srsearch=" +
                    URLEncoder.encode(term, "UTF-8") + "&format=json"
            )?.let { runCatching { JSONObject(it).getJSONObject("query").getJSONArray("search") }.getOrNull() } ?: continue
            for (i in 0 until arr.length()) {
                val title = arr.getJSONObject(i).getString("title")
                val n = title.removePrefix("File:").lowercase()
                val isImage = n.endsWith(".svg") || n.endsWith(".png")
                if (isImage && n.contains("logo") && n.filter { it.isLetterOrDigit() }.contains(key)) out.add(commonsFileUrl(title))
            }
        }
        val logos = out.take(6).toMutableList()
        if (logos.size < 6) faviconLogo(shop)?.let { if (it !in logos) logos.add(it) }
        // Images de cartes de fidélité du magasin (Wikimedia Commons).
        val cards = LinkedHashSet<String>()
        val cardWords = listOf("carte", "card", "fidel", "loyalty", "club", "member", "rewards")
        if (key.isNotEmpty()) for (term in listOf("$shop carte fidélité", "$shop loyalty card", "$shop card")) {
            val arr = getText(
                "https://commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=6&srlimit=30&srsearch=" +
                    URLEncoder.encode(term, "UTF-8") + "&format=json"
            )?.let { runCatching { JSONObject(it).getJSONObject("query").getJSONArray("search") }.getOrNull() } ?: continue
            for (i in 0 until arr.length()) {
                val title = arr.getJSONObject(i).getString("title")
                val n = title.removePrefix("File:").lowercase()
                val isImage = n.endsWith(".svg") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                if (isImage && cardWords.any { n.contains(it) } && n.filter { it.isLetterOrDigit() }.contains(key)) {
                    cards.add(commonsFileUrl(title))
                }
            }
        }
        (logos + cards.filter { it !in logos }).take(10)
    }

    private val manualImage = mutableSetOf<String>()

    fun setImage(id: String, url: String) {
        manualImage.add(id)
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(imagesDir, "${id}_${System.currentTimeMillis()}_logo.png")
                    open(url).getInputStream().use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null) {
                imagesDir.listFiles { f -> f.name.startsWith(id) && f.absolutePath != path }?.forEach { it.delete() }
                update(id) { it.copy(imagePath = path) }
            }
        }
    }

    private fun download(card: LoyaltyCard) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val src = wikidataLogo(card.shop) ?: commonsLogo(card.shop) ?: faviconLogo(card.shop) ?: return@runCatching null
                    val file = File(imagesDir, "${card.id}_logo.png")
                    open(src).getInputStream().use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                    file.absolutePath
                }.getOrNull()
            }
            if (path != null && card.id !in manualImage) {
                imagesDir.listFiles { f -> f.name.startsWith(card.id) && f.absolutePath != path }?.forEach { it.delete() }
                update(card.id) { it.copy(imagePath = path) }
            } else if (path != null) File(path).delete()
        }
    }

    fun add(shop: String, colorIndex: Int, barcode: String, format: String = "CODE_128"): String {
        val card = LoyaltyCard(
            shop = shop.trim(), colorIndex = colorIndex,
            barcode = barcode.trim().ifEmpty { randomBarcode() },
            format = if (barcode.isBlank()) "CODE_128" else format
        )
        val pos = cards.indexOfFirst { it.shop.compareTo(card.shop, ignoreCase = true) > 0 }
        cards.add(if (pos < 0) cards.size else pos, card)
        save()
        download(card)
        return card.id
    }

    var grid by mutableStateOf(prefs.getBoolean("grid", false))
        private set

    fun toggleGrid() {
        grid = !grid
        prefs.edit { putBoolean("grid", grid) }
    }

    fun move(id: String, delta: Int) {
        val i = cards.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in cards.indices) return
        val c = cards.removeAt(i)
        cards.add(j, c)
        save()
    }

    fun delete(id: String) {
        cards.removeAll { it.id == id }
        imagesDir.listFiles { f -> f.name.startsWith(id) }?.forEach { it.delete() }
        save()
    }

    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) update(id) { it.copy(shop = n) }
    }

    private fun update(id: String, f: (LoyaltyCard) -> LoyaltyCard) {
        val i = cards.indexOfFirst { it.id == id }
        if (i >= 0) {
            cards[i] = f(cards[i])
            save()
        }
    }

    private fun save() {
        val arr = JSONArray()
        cards.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("shop", it.shop)
                    .put("color", it.colorIndex).put("barcode", it.barcode).put("path", it.imagePath)
                                        .put("format", it.format)            )
        }
        prefs.edit { putString("data", arr.toString()) }
    }

    private fun load(): List<LoyaltyCard> {
        val raw = prefs.getString("data", null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (exception: JSONException) {
            Log.e("CardsViewModel", "Unable to read saved loyalty cards; starting with an empty list", exception)
            return emptyList()
        }
        return buildList {
            for (index in 0 until arr.length()) {
                try {
                    val o = arr.getJSONObject(index)
                    add(
                        LoyaltyCard(
                            o.getString("id"), o.getString("shop"), o.getInt("color").coerceAtLeast(0),
                            o.optString("barcode").ifEmpty { randomBarcode() },                             o.optString("path"),
                                                        o.optString("format").ifEmpty { "CODE_128" }                        )
                    )
                } catch (exception: JSONException) {
                    Log.e("CardsViewModel", "Skipping invalid saved loyalty card at index $index", exception)
                }
            }
        }
    }
}
