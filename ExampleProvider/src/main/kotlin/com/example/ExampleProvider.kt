package com.example

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://animexin.dev/" 
    override var name = "animexin"
    override val supportedTypes = setOf(TvType.Anime)

    override var lang = "en"
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl?s=$query").document

        return document.select("article.bs").mapNotNull { element ->
            val title = element.select("h2").text().ifEmpty { return@mapNotNull null }
            val href = fixUrl(element.select("a").attr("href")).ifEmpty { return@mapNotNull null }
            val posterUrl = fixUrl(element.select("img").attr("src")).ifEmpty { return@mapNotNull null }

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = fixUrl(document.selectFirst("div.thumb img")?.attr("src") ?: "")
        val description = document.selectFirst("div.entry-content p")?.text()?.trim()
        val genres = document.select("div.genxrel a").map { it.text() }
        
        val status = when (document.selectFirst("div.info-content span")?.text()?.contains("Completed", ignoreCase = true) == true) {
            true -> ShowStatus.Completed
            else -> ShowStatus.Ongoing
        }

        // Fixed deprecated Episode constructor -> replaced with newEpisode
        val episodes = document.select("div.episodelist ul li").mapNotNull { element ->
            val episodeHref = fixUrl(element.select("a").attr("href"))
            val episodeName = element.select("span.eps").text()
            val episodeNumber = Regex("""\d+""").find(episodeName)?.value?.toIntOrNull()

            newEpisode(episodeHref) {
                this.name = episodeName
                this.episode = episodeNumber
            }
        }.reversed()

        // Fixed DubStatus.Sub -> DubStatus.Subbed and mapped with mutableMapOf
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
            this.showStatus = status
            this.episodes = mutableMapOf(DubStatus.Subbed to episodes)
        }
    }

    // Fixed function signature by removing offset parameter
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframeUrls = mutableListOf<String>()

        val indoKeywords = listOf("indo", "indonesia", "sub indo", "subindo")

        // 1. Process server selector dropdowns
        document.select("select.mirror option, option[value]").forEach { option ->
            val labelText = option.text().lowercase()
            val rawValue = option.attr("value").trim()

            val isIndonesian = indoKeywords.any { labelText.contains(it) }

            if (!isIndonesian && rawValue.isNotEmpty()) {
                val decodedUrl = decodeEmbedValue(rawValue)
                if (decodedUrl.isNotEmpty()) {
                    iframeUrls.add(fixUrl(decodedUrl))
                }
            }
        }

        // 2. Fallback to direct iframe elements on the page
        if (iframeUrls.isEmpty()) {
            document.select("div.player-embed iframe, div.embed-responsive iframe").forEach { iframe ->
                val parentText = iframe.parent()?.text()?.lowercase() ?: ""
                val isIndonesian = indoKeywords.any { parentText.contains(it) }
                val src = iframe.attr("src")

                if (!isIndonesian && src.isNotEmpty()) {
                    iframeUrls.add(fixUrl(src))
                }
            }
        }

        // Pass English embed links to Cloudstream extractors
        iframeUrls.distinct().forEach { url ->
            loadExtractor(url, subtitleCallback, callback)
        }

        return iframeUrls.isNotEmpty()
    }

    private fun decodeEmbedValue(value: String): String {
        return try {
            val decoded = String(Base64.decode(value, Base64.DEFAULT))
            Jsoup.parse(decoded).select("iframe").attr("src").ifEmpty {
                if (value.startsWith("http")) value else ""
            }
        } catch (_: Exception) {
            if (value.startsWith("http")) value else ""
        }
    }
}
