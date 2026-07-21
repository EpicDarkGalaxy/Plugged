package com.example

import com.lagradost.cloudstream3.*
// import com.lagradost.cloudstream3.SearchResponse
// import com.lagradost.cloudstream3.TvType

class ExampleProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://animexin.dev/" 
    override var name = "animexin"
    override val supportedTypes = setOf(TvType.Anime)

    override var lang = "en"

    // Enable this when your provider has a main page
    override val hasMainPage = false

    // This function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        var document = app.get(mainUrl + "s?=$query").document

        return document.select("article.bs").mapNotNull { element ->

            val title = element.select("h2").text()
            if (title.isEmpty()) return@mapNotNull null

            val href = fixUrl(element.select("a").attr("href"))
            if (href.isEmpty()) return@mapNotNull null

            val posterUrl = fixUrl(element.select("img").attr("src"))

            listOf(
                newTvSeriesSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            )   
        }.flatten()
    }
}
