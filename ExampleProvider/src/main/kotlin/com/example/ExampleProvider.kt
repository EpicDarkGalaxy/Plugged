package com.example

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
            val title = element.select("h2").text().trim()
            val href = fixUrl(element.select("a").attr("href"))
            val posterUrl = fixUrl(element.select("img").attr("src"))

            if (title.isEmpty() || href.isEmpty()) return@mapNotNull null

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }   
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // 1. Parse basic details from the show's page
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val description = document.selectFirst("div.entry-content p")?.text()?.trim()
        val genres = document.select("div.genxrel a").map { it.text().trim() }
        
        val statusText = document.selectFirst("div.info-content span")?.text() ?: ""
        val status = when {
            statusText.contains("Completed", true) -> ShowStatus.Completed
            statusText.contains("Ongoing", true) -> ShowStatus.Ongoing
            else -> null
        }

        // 2. Parse episodes list using newEpisode
        val episodes = document.select("div.eplister ul li").mapNotNull { element ->
            val episodeHref = fixUrl(element.select("a").attr("href"))
            if (episodeHref.isEmpty()) return@mapNotNull null
            
            val episodeName = element.select("div.epl-title").text().trim()
            val episodeNumber = element.select("div.epl-num").text().toIntOrNull()

            newEpisode(episodeHref) {
                this.name = episodeName
                this.episode = episodeNumber
            }
        }.reversed()

        // 3. Return using newAnimeLoadResponse with proper properties
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrl(document.selectFirst("div.thumb img")?.attr("src") ?: "")
            this.plot = description
            this.tags = genres
            this.showStatus = status 
            
            addEpisodes(DubStatus.Subbed, episodes) 
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Keywords signaling Indonesian language streams
        val indoKeywords = listOf("indo", "indonesia", "sub indo", "subindo")

        // Select option elements from mirror dropdown
        val options = document.select("select.mirror option")

        for (option in options) {
            val optionText = option.text().lowercase()
            val encodedString = option.attr("value").trim()

            // Skip options that explicitly match Indonesian sub tags
            val isIndonesian = indoKeywords.any { optionText.contains(it) }
            if (isIndonesian || encodedString.isBlank()) continue

            try {
                // Decode Base64 snippet to extract the actual iframe source
                val decodedHtml = String(Base64.decode(encodedString, Base64.DEFAULT))
                val iframeDocument = Jsoup.parse(decodedHtml)
                val iframeUrl = iframeDocument.select("iframe").attr("src")

                if (iframeUrl.isNotBlank()) {
                    loadExtractor(
                        url = fixUrl(iframeUrl),
                        referer = data,
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    }
}
