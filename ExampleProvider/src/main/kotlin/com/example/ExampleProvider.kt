package com.example

import com.lagradost.cloudstream3.*
// import com.lagradost.cloudstream3.SearchResponse
// import com.lagradost.cloudstream3.TvType

class ExampleProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://animexin.dev/" 
    override var name = "animexin"
    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "en"

    // Enable this when your provider has a main page
    override val hasMainPage = false

    // This function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        var document = app.get(mainUrl + "search?keyword=$query").document

        return document.select("div.bs").mapNotNull {
            element ->
            val title = element.select("h2").text()
            val href = fixUrl(element.select("a").attr("href"))
            val posterUrl = fixUrl(element.select("img").attr("src"))

            return newAnimeSearchResponse(
                name = title,
                url = href,
                TvType = TvType.Anime
            ) {
                this.poster = posterUrl
            }
        }
    }
}