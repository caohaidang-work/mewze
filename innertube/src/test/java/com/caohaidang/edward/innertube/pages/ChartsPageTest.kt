package com.caohaidang.edward.innertube.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChartsPageTest {

    private fun row(id: String) = """
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$id"},
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://img/$id.jpg"}]}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song $id"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Artist $id","navigationEndpoint":{"browseEndpoint":{"browseId":"UC$id"}}}]}}}
          ]}}"""

    private fun card(id: String) = """
        {"musicTwoRowItemRenderer":{
          "title":{"runs":[{"text":"Video $id"}]},
          "subtitle":{"runs":[{"text":"Channel $id","navigationEndpoint":{"browseEndpoint":{"browseId":"UC$id"}}}]},
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://img/$id.jpg"}]}}},
          "navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}}}"""

    private fun carousel(title: String, items: List<String>) = """
        {"musicCarouselShelfRenderer":{
          "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"$title"}]}}},
          "contents":[${items.joinToString(",")}]}}"""

    private fun shelf(title: String, items: List<String>) = """
        {"musicShelfRenderer":{"title":{"runs":[{"text":"$title"}]},"contents":[${items.joinToString(",")}]}}"""

    private fun page(vararg sections: String) = """
        {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
          "sectionListRenderer":{"contents":[${sections.joinToString(",")}]}}}}]}}}"""

    private fun ids(prefix: String) = (1..5).map { "$prefix$it" }

    @Test
    fun picksTopSongsNotVideoCharts() {
        val body = page(
            carousel("Video charts", ids("v").map(::card)),
            shelf("Top songs", ids("s").map(::row)),
            carousel("Trending", ids("t").map(::card)),
        )
        val chart = ChartsPage.fromJson("VN", body)
        assertNotNull(chart)
        assertEquals(ids("s"), chart!!.songs.map { it.id })
        assertEquals("Song s1", chart.songs.first().title)
        assertEquals("Artist s1", chart.songs.first().artists.first().name)
    }

    @Test
    fun picksVietnameseTopSongsTitle() {
        val body = page(
            carousel("Bảng xếp hạng video", ids("v").map(::card)),
            carousel("Bài hát hàng đầu", ids("s").map(::row)),
        )
        assertEquals(ids("s"), ChartsPage.fromJson("VN", body)!!.songs.map { it.id })
    }

    @Test
    fun usesRankedRowsWhenTitleIsUnknown() {
        val body = page(
            carousel("Video charts", ids("v").map(::card)),
            shelf("Something else", ids("s").map(::row)),
        )
        assertEquals(ids("s"), ChartsPage.fromJson("ZZ", body)!!.songs.map { it.id })
    }

    @Test
    fun returnsNullInsteadOfVideosOrTrending() {
        val body = page(
            carousel("Video charts", ids("v").map(::card)),
            carousel("Trending", ids("t").map(::card)),
            shelf("Top artists", ids("a").map(::row)),
        )
        assertNull(ChartsPage.fromJson("VN", body))
    }

    @Test
    fun topSongsShelfMadeOfCardsIsStillUsed() {
        val body = page(
            carousel("Video charts", ids("v").map(::card)),
            carousel("Top songs", ids("s").map(::card)),
        )
        assertEquals(ids("s"), ChartsPage.fromJson("US", body)!!.songs.map { it.id })
    }
}
