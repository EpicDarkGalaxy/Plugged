package com.example

import com.lagradost.cloudstream3.*

class ExampleProvider : MainAPI() { 
    override var mainUrl = "https://animexin.dev/" 
    override var name = "animexin"
    override val supportedTypes = setOf(TvType.Anime)

    override var lang = "en"
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl?s=$query").document

        return document.select("article.bs").mapNotNull { element ->

            val title = element.select("h2").text()
            if (title.isEmpty()) return@mapNotNull null

            val href = fixUrl(element.select("a").attr("href"))
            if (href.isEmpty()) return@mapNotNull null

            val posterUrl = fixUrl(element.select("img").attr("src"))
            if (posterUrl.isEmpty()) return@mapNotNull null

            listOf(
                newTvSeriesSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            )   
        }.flatten()
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // 1. Parse basic details from the show's page
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        
        // Fixed: Ensure poster is guaranteed to be a non-null String using an empty string fallback
        val poster: String = fixUrlNull(document.selectFirst("div.thumb img")?.attr("src")) ?: ""
        
        val description = document.selectFirst("div.entry-content p")?.text()?.trim()
        val genres = document.select("div.genxrel a").map { it.text() }

        // 2. Parse episodes list using the recommended newEpisode method
        val episodes = document.select("div.episodelist ul li").mapNotNull { element ->
            val episodeHref = fixUrl(element.select("a").attr("href"))
            if (episodeHref.isEmpty()) return@mapNotNull null
            
            val episodeName = element.select("span.eps").text()
            val episodeNumber = Regex("""\d+""").find(episodeName)?.value?.toIntOrNull()

            newEpisode(episodeHref) {
                this.name = episodeName
                this.episode = episodeNumber
            }
        }.reversed()

        // 3. Return the proper LoadResponse type
        return newTvSeriesSearchResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
        }
    }
}
