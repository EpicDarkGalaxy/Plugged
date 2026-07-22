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
            val title = element.select("h2").text().trim()
            val href = fixUrl(element.select("a").attr("href"))
            val posterUrl = fixUrl(element.select("img").attr("src"))

            // If we are missing crucial data, skip this element
            if (title.isEmpty() || href.isEmpty()) return@mapNotNull null

            // Return AnimeSearchResponse directly
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
        
        // Status parsing matching ShowStatus enum correctly
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
            val episodeNumber = Regex("""\d+""").find(episodeName)?.value?.toIntOrNull()

            newEpisode(episodeHref) {
                this.name = episodeName
                this.episode = episodeNumber
            }
        }.reversed() // Reverse so Episode 1 comes first (anime sites usually list newest first)

        // 3. Return using newAnimeLoadResponse with proper properties
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrl(document.selectFirst("div.thumb img")?.attr("src") ?: "")
            this.plot = description
            this.tags = genres
            this.showStatus = status 
            
            // Assign the episodes list to a Subbed or Dubbed category
            addEpisodes(DubStatus.Subbed, episodes) 
            
            // Note: If your Cloudstream version doesn't recognize addEpisodes, 
            // you can use: this.episodes[DubStatus.Subbed] = episodes
        }
    }
}
